# 参数收集和确认流程 - 使用指南

## 概述

参数收集和确认流程是 Assistant Agent 的核心功能，它能够：
- 匹配用户的动作意图
- 自动提取参数
- 缺失参数时发起追问
- 参数收集完成后请求确认
- 确认后执行 HTTP API 调用

## 配置

### 1. 启用参数收集功能

在 `application.yml` 中添加配置：

```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          planning:
            enabled: true
            matching:
              # 启用参数收集流程
              param-collection-enabled: true
```

### 2. 配置权重和阈值（可选）

```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          planning:
            matching:
              # 关键词匹配权重（0-1）
              keyword-weight: 0.4
              # 语义匹配权重（0-1）
              semantic-weight: 0.6
              # 匹配阈值（0-1）
              threshold: 0.3  # 降低阈值以提高匹配率
```

## 完整流程示例

### 场景：添加产品单位

#### 数据库 Action 定义

```sql
INSERT INTO `action_registry` (
  `action_id`, `action_name`, `description`, `action_type`,
  `keywords`, `synonyms`, `parameters`, `interface_binding`
) VALUES (
  'erp:product-unit:create',
  '添加产品单位',
  '在ERP系统中创建新的产品计量单位',
  'API_CALL',
  '["添加单位", "新建单位", "创建单位"]',
  '["加个单位", "建个单位"]',
  '[
    {
      "name": "name",
      "label": "单位名称",
      "type": "STRING",
      "required": true,
      "description": "计量单位名称，如：个、台、箱、件等"
    },
    {
      "name": "status",
      "label": "单位状态",
      "type": "ENUM",
      "required": false,
      "enumValues": ["0", "1"],
      "description": "0=禁用，1=启用",
      "defaultValue": "0"
    }
  ]',
  '{
    "type": "HTTP",
    "http": {
      "url": "https://api.simplify.devefive.com/admin-api/erp/product-unit/create",
      "method": "POST",
      "headers": {
        "tenant-id": "1",
        "Content-Type": "application/json"
      }
    }
  }'
);
```

#### 用户交互流程

```
用户: 添加产品单位

系统: [匹配到 action，创建会话，提取参数 → 无参数]
      [验证参数 → 缺失 name]

系统: 请输入单位名称（计量单位名称，如：个、台、箱、件等）

用户: 个

系统: [提取参数 → {name: "个"}]
      [验证参数 → 所有参数完整]

系统: 请确认以下信息：
      - 单位名称: 个
      - 单位状态: 0 (默认)

      回复"确认"执行，或修改参数

用户: 确认

系统: [执行 HTTP API 调用]
      [返回结果]

系统: ✓ 操作成功完成
      单位"个"已添加到系统中
```

## 评估结果格式

### 1. 匹配成功（未启用参数收集或无参数）

```
MATCHED|erp:product-unit:create|添加产品单位|0.95|KEYWORD_EXACT
```

### 2. 参数收集进行中

```
PARAM_COLLECTION|abc-123-def|erp:product-unit:create|COLLECTING|请输入单位名称|true|false
```

格式说明：
- `PARAM_COLLECTION` - 固定前缀
- `abc-123-def` - 参数收集会话 ID
- `erp:product-unit:create` - 动作 ID
- `COLLECTING` - 会话状态（COLLECTING/PENDING_CONFIRM/CONFIRMED/COMPLETED）
- `请输入单位名称` - 追问消息（已转义）
- `true` - 是否需要输入
- `false` - 是否需要确认

### 3. 待确认

```
PARAM_COLLECTION|abc-123-def|erp:product-unit:create|PENDING_CONFIRM|请确认以下信息|false|true
```

### 4. 未匹配

```
NO_MATCH
```

## 元数据格式

评估结果还包含 `metadata` 字段，用于前端集成：

```json
{
  "paramCollectionSessionId": "abc-123-def",
  "actionId": "erp:product-unit:create",
  "actionName": "添加产品单位",
  "state": "COLLECTING",
  "requiresInput": true,
  "requiresConfirmation": false,
  "completed": false,
  "message": "请输入单位名称"
}
```

## 前端集成示例

### 解析评估结果

```java
String resultValue = criterionResult.getValue();
Map<String, Object> metadata = criterionResult.getMetadata();

if (resultValue.startsWith("PARAM_COLLECTION")) {
    // 参数收集流程
    String sessionId = (String) metadata.get("paramCollectionSessionId");
    String state = (String) metadata.get("state");
    String message = (String) metadata.get("message");
    boolean requiresInput = (boolean) metadata.get("requiresInput");
    boolean requiresConfirmation = (boolean) metadata.get("requiresConfirmation");

    if (requiresInput) {
        // 显示输入框
        showMessage(message);
        awaitUserInput();
    } else if (requiresConfirmation) {
        // 显示确认卡片
        showConfirmationCard(sessionId);
        awaitConfirmation();
    }
} else if (resultValue.startsWith("MATCHED")) {
    // 直接匹配，无需参数收集
    executeAction();
}
```

### 前端状态管理

```javascript
// 存储会话信息
const sessionContext = {
  paramCollectionSessionId: metadata.paramCollectionSessionId,
  actionId: metadata.actionId,
  state: metadata.state
};

// 在下次请求中携带上下文
const request = {
  userInput: userInput,
  context: {
    contextMetadata: sessionContext
  }
};
```

## API 说明

### ParamCollectionService

主要方法：

```java
// 创建参数收集会话
ParamCollectionSession createSession(ActionDefinition action, String assistantSessionId, String userId)

// 处理用户输入
ProcessResult processUserInput(ParamCollectionSession session, ActionDefinition action, String userInput, List<String> chatHistory)

// 确认并执行
ProcessResult confirmAndExecute(ParamCollectionSession session, ActionDefinition action)

// 取消会话
void cancelSession(String sessionId)
```

### ProcessResult 字段说明

```java
boolean success;              // 是否成功
boolean completed;            // 是否完成（所有参数已收集）
boolean requiresInput;        // 是否需要用户输入
boolean requiresConfirmation; // 是否需要确认
String message;               // 返回给用户的消息
List<MissingParamInfo> missingParams;      // 缺失的参数列表
List<ConfirmationParam> confirmationParams; // 确认参数列表
ExecutionResult executionResult;           // 执行结果
```

## 故障排查

### 1. Action 无法匹配

**症状**: 输入"添加单位"时返回 `NO_MATCH`

**原因**: 匹配阈值过高

**解决方案**:
```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          planning:
            matching:
              threshold: 0.3  # 从 0.5 降到 0.3
              keyword-weight: 0.6  # 提高关键词权重
```

### 2. 参数未被提取

**症状**: 系统一直追问参数

**原因**: LLM 提取失败

**解决方案**:
- 检查日志中的 LLM 响应
- 优化 prompt 模板（StructuredParamExtractor.DEFAULT_EXTRACT_PROMPT）
- 确保参数定义有清晰的 description

### 3. 参数验证失败

**症状**: 参数提取后仍显示缺失

**原因**: 参数类型不匹配或格式错误

**解决方案**:
- 检查 ActionParameter 的 type 定义
- 确保参数值符合类型要求
- 查看日志中的验证错误信息

## 最佳实践

### 1. 参数定义

```json
{
  "name": "phone",
  "label": "手机号",
  "type": "STRING",
  "required": true,
  "description": "11位手机号码",
  "pattern": "^1[3-9]\\d{9}$",
  "placeholder": "13800138000"
}
```

### 2. 枚举参数

```json
{
  "name": "status",
  "label": "状态",
  "type": "ENUM",
  "required": true,
  "enumValues": ["0", "1"],
  "description": "0=禁用，1=启用",
  "defaultValue": "1"
}
```

### 3. 关键词优化

```sql
-- 添加多样化的关键词和同义词
'keywords': '["添加单位", "新建单位", "创建单位", "新增单位", "加单位"]'
'synonyms': '["加个单位", "建个单位", "录入单位", "增加单位"]'
```

### 4. 使用 Parameter Options Service

从 v0.1.1 开始，支持动态参数选项：

```json
{
  "name": "unitId",
  "label": "产品单位",
  "type": "STRING",
  "required": true,
  "description": "选择产品计量单位",
  "optionsSource": {
    "type": "HTTP",
    "systemId": "erp-system",
    "config": {
      "url": "https://api.example.com/units",
      "method": "GET",
      "labelPath": "$.data[*].name",
      "valuePath": "$.data[*].id"
    }
  }
}
```

详见: [Parameter Options Service 文档](docs/PARAMETER_OPTIONS_SERVICE.md)

---

## Action Parameter Schema 规范

### 完整 Schema 定义

```typescript
interface ActionParameter {
  // 基础属性
  name: string;              // 参数名称（必填）
  label?: string;            // 显示标签
  type: ParameterType;       // 参数类型（必填）
  required: boolean;         // 是否必填（默认 false）
  description?: string;      // 参数说明

  // 默认值和占位符
  defaultValue?: any;        // 默认值
  placeholder?: string;      // 输入提示

  // 验证规则
  pattern?: string;          // 正则表达式
  minLength?: number;        // 最小长度
  maxLength?: number;        // 最大长度
  min?: number;              // 最小值（数字类型）
  max?: number;              // 最大值（数字类型）

  // 枚举类型专用
  enumValues?: string[];     // 枚举值列表

  // 动态选项（v0.1.1+）
  optionsSource?: {
    type: 'STATIC' | 'HTTP' | 'NL2SQL' | 'ENUM';
    systemId?: string;
    config: object;
  };

  // 参数依赖（未来版本）
  dependsOn?: string[];      // 依赖的其他参数
  visibleWhen?: string;      // 显示条件表达式
}

enum ParameterType {
  STRING = 'STRING',
  NUMBER = 'NUMBER',
  BOOLEAN = 'BOOLEAN',
  ENUM = 'ENUM',
  DATE = 'DATE',
  ARRAY = 'ARRAY',
  OBJECT = 'OBJECT'
}
```

### 各类型参数示例

#### STRING 类型

```json
{
  "name": "productName",
  "label": "产品名称",
  "type": "STRING",
  "required": true,
  "description": "产品的完整名称",
  "minLength": 2,
  "maxLength": 50,
  "placeholder": "请输入产品名称"
}
```

#### NUMBER 类型

```json
{
  "name": "price",
  "label": "价格",
  "type": "NUMBER",
  "required": true,
  "description": "产品售价（元）",
  "min": 0,
  "max": 999999.99,
  "placeholder": "0.00"
}
```

#### BOOLEAN 类型

```json
{
  "name": "isPublic",
  "label": "是否公开",
  "type": "BOOLEAN",
  "required": false,
  "description": "是否对外公开显示",
  "defaultValue": true
}
```

#### ENUM 类型

```json
{
  "name": "status",
  "label": "状态",
  "type": "ENUM",
  "required": true,
  "enumValues": ["DRAFT", "PUBLISHED", "ARCHIVED"],
  "description": "文档状态",
  "defaultValue": "DRAFT"
}
```

#### DATE 类型

```json
{
  "name": "publishDate",
  "label": "发布日期",
  "type": "DATE",
  "required": false,
  "description": "计划发布日期",
  "placeholder": "YYYY-MM-DD"
}
```

#### ARRAY 类型

```json
{
  "name": "tags",
  "label": "标签",
  "type": "ARRAY",
  "required": false,
  "description": "产品标签列表",
  "defaultValue": []
}
```

---

## Prompt Template 参考

### 参数提取 Prompt

系统使用以下 prompt 从用户输入中提取参数：

```
你是一个参数提取助手。用户想要执行以下操作：

**操作名称**: {actionName}
**操作描述**: {actionDescription}

**需要的参数**:
{parameters}

**用户输入**: {userInput}

请从用户输入中提取参数值，以 JSON 格式返回。如果某个参数无法提取，设置为 null。

示例输出:
{
  "name": "个",
  "status": null
}
```

### 参数追问 Prompt

当参数缺失时，系统使用以下 prompt 生成追问：

```
用户正在执行操作：{actionName}

已收集的参数:
{collectedParams}

缺失的必填参数:
- {paramLabel} ({paramName}): {paramDescription}

请生成一个自然、友好的追问，询问用户提供缺失的参数值。
追问应该:
1. 简洁明了
2. 说明参数的用途
3. 如果有枚举值，提供选项
4. 如果有示例值，给出示例

示例追问:
"请问产品单位的名称是什么？例如：个、台、箱、件等"
```

### 确认 Prompt

参数收集完成后，系统使用以下 prompt 请求确认：

```
参数已收集完成，请确认以下信息：

**操作**: {actionName}
{parameters}

是否确认执行？(回复"确认"或"取消")
```

---

## 系统集成指南

### 1. 集成到现有 Spring Boot 应用

#### 1.1 添加依赖

```xml
<dependency>
    <groupId>com.alibaba.agent.assistant</groupId>
    <artifactId>assistant-agent-planning-core</artifactId>
    <version>0.1.1</version>
</dependency>
```

#### 1.2 启用自动配置

```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          planning:
            enabled: true
            matching:
              param-collection-enabled: true
```

#### 1.3 注入服务

```java
@Service
public class MyBusinessService {

    @Autowired
    private ParameterCollectionOrchestrator orchestrator;

    @Autowired
    private ActionMatcher actionMatcher;

    public void handleUserInput(String userInput, String userId) {
        // 1. 匹配 Action
        List<ActionDefinition> matches = actionMatcher.match(userInput);

        if (matches.isEmpty()) {
            // 未匹配到 Action
            return;
        }

        ActionDefinition action = matches.get(0);

        // 2. 启动参数收集流程
        orchestrator.startCollection(userId, action, userInput);
    }
}
```

### 2. 自定义 Action Provider

实现 `ActionProvider` SPI 以提供自定义的 Action 定义：

```java
@Component
public class CustomActionProvider implements ActionProvider {

    @Override
    public List<ActionDefinition> getAllActions() {
        // 从数据库、配置文件或其他来源加载 Action
        return loadActionsFromDatabase();
    }

    @Override
    public ActionDefinition getActionById(String actionId) {
        return findActionByIdFromDatabase(actionId);
    }

    @Override
    public String getName() {
        return "CustomActionProvider";
    }

    private List<ActionDefinition> loadActionsFromDatabase() {
        // 实现数据库查询逻辑
        return List.of();
    }
}
```

### 3. 自定义参数收集策略

实现 `ParameterCollectionStrategy` 接口以自定义参数收集行为：

```java
@Component
public class CustomCollectionStrategy implements ParameterCollectionStrategy {

    @Override
    public boolean shouldAskForParameter(
            ActionParameter parameter,
            Map<String, Object> collectedParams) {
        // 自定义逻辑：决定是否需要追问该参数
        if (parameter.getDefaultValue() != null) {
            // 有默认值，不追问
            return false;
        }
        return parameter.isRequired();
    }

    @Override
    public String generateQuestion(
            ActionParameter parameter,
            Map<String, Object> collectedParams) {
        // 自定义追问内容生成
        return "请提供 " + parameter.getLabel() + "：" +
               parameter.getDescription();
    }
}
```

### 4. 集成外部系统

#### 4.1 HTTP API 调用

```java
@Component
public class ApiExecutor {

    @Autowired
    private RestTemplate restTemplate;

    public ExecutionResult executeAction(
            ActionDefinition action,
            Map<String, Object> parameters) {

        HttpBinding httpBinding = action.getInterfaceBinding().getHttp();

        // 构建请求
        HttpHeaders headers = new HttpHeaders();
        httpBinding.getHeaders().forEach(headers::add);

        HttpEntity<Map<String, Object>> request =
            new HttpEntity<>(parameters, headers);

        // 发送请求
        ResponseEntity<String> response = restTemplate.exchange(
            httpBinding.getUrl(),
            HttpMethod.valueOf(httpBinding.getMethod()),
            request,
            String.class
        );

        // 返回结果
        return ExecutionResult.builder()
            .success(response.getStatusCode().is2xxSuccessful())
            .responseBody(response.getBody())
            .build();
    }
}
```

#### 4.2 MCP 集成

```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          dynamic:
            mcp:
              servers:
                - name: erp-system
                  command: npx
                  args: ["-y", "@modelcontextprotocol/server-everything"]
                  env:
                    API_KEY: ${ERP_API_KEY}
```

### 5. 会话管理

#### 5.1 实现 SessionProvider

```java
@Component
public class RedisSessionProvider implements SessionProvider {

    @Autowired
    private RedisTemplate<String, ParamCollectionSession> redisTemplate;

    @Override
    public void saveSession(String sessionId, ParamCollectionSession session) {
        redisTemplate.opsForValue().set(
            "session:" + sessionId,
            session,
            Duration.ofHours(1)
        );
    }

    @Override
    public ParamCollectionSession getSession(String sessionId) {
        return redisTemplate.opsForValue().get("session:" + sessionId);
    }

    @Override
    public void deleteSession(String sessionId) {
        redisTemplate.delete("session:" + sessionId);
    }
}
```

### 6. 监控和日志

#### 6.1 添加 Metrics

```java
@Component
public class CollectionMetrics {

    private final MeterRegistry registry;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer collectionTimer;

    public CollectionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.successCounter = Counter.builder("param_collection_success")
            .description("Successful parameter collections")
            .register(registry);
        this.failureCounter = Counter.builder("param_collection_failure")
            .description("Failed parameter collections")
            .register(registry);
        this.collectionTimer = Timer.builder("param_collection_duration")
            .description("Parameter collection duration")
            .register(registry);
    }

    public void recordSuccess() {
        successCounter.increment();
    }

    public void recordFailure() {
        failureCounter.increment();
    }

    public void recordDuration(long milliseconds) {
        collectionTimer.record(Duration.ofMillis(milliseconds));
    }
}
```

#### 6.2 配置日志

```yaml
logging:
  level:
    com.alibaba.assistant.agent.planning: INFO
    com.alibaba.assistant.agent.planning.matching: DEBUG
    com.alibaba.assistant.agent.planning.param: DEBUG
```

---

## 扩展功能

### 参数依赖关系（计划中）

未来版本将支持参数间的依赖关系：

```json
{
  "name": "city",
  "label": "城市",
  "type": "STRING",
  "required": true,
  "dependsOn": ["province"],
  "optionsSource": {
    "type": "HTTP",
    "config": {
      "url": "https://api.example.com/cities?province={province}"
    }
  }
}
```

### 条件显示（计划中）

基于其他参数值决定是否显示某个参数：

```json
{
  "name": "invoiceTitle",
  "label": "发票抬头",
  "type": "STRING",
  "required": true,
  "visibleWhen": "needInvoice === true"
}
```

### 多语言支持（计划中）

支持参数标签和描述的国际化：

```json
{
  "name": "productName",
  "label": {
    "zh-CN": "产品名称",
    "en-US": "Product Name"
  },
  "description": {
    "zh-CN": "产品的完整名称",
    "en-US": "Full name of the product"
  }
}
```

---

## 相关文档

- [Parameter Options Service 文档](docs/PARAMETER_OPTIONS_SERVICE.md)
- [Action Definition Schema](../docs/ACTION_DEFINITION_SCHEMA.md)
- [API 参考文档](../docs/API_REFERENCE.md)
- [Spring AI Alibaba 文档](https://github.com/alibaba/spring-ai-alibaba)

---

**文档版本**: 1.1.0
**最后更新**: 2026-01-20
**作者**: Assistant Agent Team
