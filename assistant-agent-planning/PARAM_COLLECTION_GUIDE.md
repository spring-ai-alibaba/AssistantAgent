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

## 下一步

1. 编写单元测试
2. 添加性能监控
3. 实现会话持久化（Redis）
4. 优化 LLM prompt 模板
5. 添加多语言支持
