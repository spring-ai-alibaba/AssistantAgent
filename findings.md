# Findings: assistant-management 项目分析

## 项目信息
- **路径**: D:\devfive\dataagent\assistant-management
- **类型**: 企业 AI 助手平台 - 智能规划引擎
- **框架**: Spring Boot 3.4+, Spring AI Alibaba 1.0.0.4
- **核心能力**: 意图识别、知识问答(RAG)、数据查询(DataAgent)、动作执行、多步骤编排

## 项目结构

```
assistant-management/
├── entity/          # ActionRegistry, ActionExecutionLog, MultiStepActionState
├── dto/             # ActionDefinition, ActionMatch, ActionResult
├── service/         # ActionService, KnowledgeService, MultiStepActionService
├── executor/        # StepExecutor, QueryStepExecutor, ExecuteStepExecutor
├── workflow/node/   # StateGraph 工作流节点
├── adapter/         # InterfaceAdapter, HttpInterfaceAdapter
└── resources/
    └── multi-step-actions/  # YAML 配置文件
```

## 动作系统（核心）

### 1. 动作注册表 (ActionRegistry Entity)
```java
- actionId: 动作唯一标识
- actionName: 动作名称
- description: 动作描述
- keywords: 关键词数组（JSON）
- synonyms: 同义词数组（JSON）
- parameters: 参数定义（JSON）
- actionType: API_CALL/PAGE_NAVIGATION/FORM_PREFILL/WORKFLOW_TRIGGER/MULTI_STEP
- handler: 处理器类名
- category: 分类
- steps: 多步骤配置（JSON，仅MULTI_STEP类型）
- stateSchema: 状态Schema（JSON，仅MULTI_STEP类型）
- timeoutMinutes: 超时时间（分钟）
- priority: 优先级
- usageCount: 使用次数
- successRate: 成功率
```

### 2. 动作类型
- **API_CALL**: 调用外部API
- **PAGE_NAVIGATION**: 页面跳转
- **FORM_PREFILL**: 表单预填
- **WORKFLOW_TRIGGER**: 触发工作流
- **MULTI_STEP**: 多步骤编排（核心）

## 参数标准（ActionParameter）

```java
class ActionParameter {
    String name;           // 参数名
    String label;          // 显示标签
    String type;           // string/number/boolean/enum/array/object
    List<String> values;   // 枚举值列表
    Boolean required;      // 是否必填
    Integer minLength;     // 最小长度
    Integer maxLength;     // 最大长度
    String pattern;        // 正则表达式
    String defaultValue;   // 默认值
    String placeholder;    // 占位符
    String description;    // 描述
}
```

### 参数来源类型（Source）
- **USER_INPUT**: 用户输入
- **CONTEXT**: 上下文（如 userId, sessionId）
- **PREVIOUS_STEP**: 前序步骤输出
- **SYSTEM**: 系统自动填充

## 多步骤执行逻辑

### 步骤类型（StepType）
1. **QUERY**: 查询数据（如：查询可申领电脑列表）
2. **INPUT**: 收集用户输入（如：填写申领理由）
3. **EXECUTE**: 执行动作（如：提交申请）
4. **API_CALL**: 调用外部API（如：查询审批人）
5. **INTERNAL_SERVICE**: 调用内部服务（如：校验信息）
6. **NOTIFICATION**: 发送通知

### 执行流程
```
1. MultiStepActionNode 加载/创建状态
   ↓
2. 根据 current_step_index 确定当前步骤
   ↓
3. StepExecutor 执行步骤
   ├─ QueryStepExecutor: 查询数据 → 返回选项 → interrupt（等待用户选择）
   ├─ InputStepExecutor: 验证输入 → 存储到 state_data → 继续下一步
   └─ ExecuteStepExecutor: 调用接口 → 返回最终结果
   ↓
4. 更新 MultiStepActionState
   ↓
5. 如果未完成，返回提示，等待下次用户输入
   ↓
6. 用户回复 → resume with threadId → 继续执行
```

### 状态管理（MultiStepActionState）
```java
- stateId: 状态唯一ID（UUID）
- sessionId: 会话ID
- actionId: 动作ID
- current_step_index: 当前步骤索引
- current_step_id: 当前步骤ID
- state_data: 状态数据（JSON）- 存储中间结果
- user_inputs: 用户输入历史（JSON）
- status: in_progress/completed/cancelled/timeout
- expire_time: 过期时间
```

## 外键依赖处理

### 参数校验步骤
1. **check_foreign_key**: 检查外键是否存在
   - 示例：检查品牌名 "Tesla" 是否存在于 brand 表
   - 如果存在，获取 brand_id
   - 如果不存在，提示用户或创建新记录

2. **参数来源映射**:
```yaml
inputParams:
  - name: brand_id
    type: STRING
    required: true
    source:
      sourceType: PREVIOUS_STEP
      sourceRef: check-brand
      expression: "$.brandInfo.brandId"  # JSONPath 提取
```

## 知识库对接

### RAG 流程
1. **向量化**: EmbeddingService → VectorStoreService
2. **检索**: Elasticsearch HNSW 索引查询
3. **重排序**: 基于相似度和业务规则
4. **答案生成**: LLM 结合检索内容生成答案

### 集成点
- **KnowledgeService**: 知识库管理
- **VectorStoreService**: 向量存储
- **ActionVectorizationService**: 动作向量化（用于语义匹配）

## 示例配置（leave-apply.yaml）

### 步骤1: 校验请假信息（INTERNAL_SERVICE）
- 输入: leaveType, startDate, endDate（来自 USER_INPUT）
- 输出: validationResult（包含 isValid, remainingDays, message）
- 接口: 内部Bean调用 leaveValidationService.validateLeaveRequest()

### 步骤2: 查询审批人（API_CALL）
- 输入: employeeId（来自 CONTEXT），leaveDays（来自 PREVIOUS_STEP）
- 输出: approverInfo（包含 approverId, approverName, approverEmail）
- 接口: HTTP REST API 调用 hr-service

### 步骤3: 提交请假申请（API_CALL + SAGA）
- 输入: 综合前序步骤的所有数据
- 输出: submitResult（包含 requestId, status）
- 接口: HTTP REST API 调用 oa-service
- **补偿机制**: 如果失败，调用 cancel 接口回滚

### 步骤4: 发送通知（NOTIFICATION）
- 输入: approverEmail, requestId（来自 PREVIOUS_STEP）
- 接口: 内部通知服务
- 策略: skippable=true（失败不影响整体流程）

## 关键技术点

1. **StateGraph 工作流引擎**: Spring AI Alibaba Graph
2. **状态持久化**: MySQL 存储 MultiStepActionState
3. **中断与恢复**: interrupt → 等待用户输入 → resume with threadId
4. **SAGA 事务**: 支持补偿机制，失败自动回滚
5. **接口适配器**: HttpInterfaceAdapter, McpToolAdapter, InternalServiceAdapter
6. **参数映射**: JSONPath 表达式提取前序步骤输出
7. **流式响应**: SSE (Server-Sent Events) 实时推送
