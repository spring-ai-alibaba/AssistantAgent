# Task Plan: 为 Assistant Agent 实现参数收集和确认流程

## 目标
为 Assistant Agent 项目实现类似 assistant-management 的参数收集和确认流程，包括：
1. 匹配 action（基于 keywords）
2. 解析和验证参数
3. 缺失参数时发起追问
4. 参数收集完成后请求用户确认
5. 确认后执行 action

## 参考实现
- **项目路径**: `D:\devfive\dataagent\assistant-management`
- **入口**: `AssistantController#chatStreamSSE`
- **核心服务**:
  - `ParamCollectionService` - 参数收集服务
  - `StructuredParamExtractor` - LLM 参数提取器
  - `SchemaValidator` - 参数校验器
  - `ParamPromptGenerator` - 追问生成器

---

## 阶段

### Phase 1: 需求分析和设计
**状态**: `completed`
**目标**: 理解参考实现并设计适配方案

**已完成**:
- ✅ 探索了 assistant-management 的参数收集流程
- ✅ 理解了 ActionSchemaV2、ParamCollectionSession 等核心概念
- ✅ 分析了入口点和数据流

**关键发现**:
1. **会话状态管理**: 使用 `ParamCollectionSession` 跟踪参数收集进度
2. **多轮对话**: 通过 sessionId 和 context 传递状态
3. **LLM 参数提取**: 使用 prompt 模板从用户输入中提取结构化参数
4. **参数验证**: 基于 schema 进行类型、必填项、格式验证
5. **确认机制**: 参数收集完成后生成确认卡片，等待用户确认
6. **SSE 流式响应**: 实时推送进度和状态更新

---

### Phase 2: 设计适配方案
**状态**: `in_progress`
**目标**: 为 Assistant Agent 设计适配的参数收集流程

**核心组件映射**:

| assistant-management | Assistant Agent | 说明 |
|---------------------|-----------------|------|
| ActionSchemaV2 | ActionDefinition | 动作定义（已存在） |
| ActionParameter | ActionParameter | 参数定义（已存在） |
| ParamCollectionSession | ParamCollectionSession | 需要新增 |
| ParamCollectionService | ParamCollectionService | 需要新增 |
| StructuredParamExtractor | StructuredParamExtractor | 需要新增 |
| SchemaValidator | ParameterValidator | 需要新增 |
| ParamPromptGenerator | PromptBuilder | 可复用现有 |
| ActionExecutor | ActionExecutor | 需要新增 |

**集成点**:
1. **ActionIntentEvaluator** → 返回匹配的 action 后，触发参数收集
2. **PlanningEvaluationHook** → 在评估后注入参数收集逻辑
3. **CodeactAgent** → 执行 action 前，确保参数已收集并确认

**数据流设计**:
```
用户输入 "添加产品单位"
  ↓
ActionIntentEvaluator 匹配到 erp:product-unit:create
  ↓
检查是否有活跃的 ParamCollectionSession
  ↓
如果没有：创建新会话，提取参数
  ↓
StructuredParamExtractor 使用 LLM 提取参数
  ↓
ParameterValidator 验证参数（缺失 name）
  ↓
生成追问："请输入单位名称"
  ↓
用户输入 "个"
  ↓
更新会话参数，再次验证
  ↓
所有参数完整，生成确认卡片
  ↓
用户确认
  ↓
执行 ActionExecutor 调用 HTTP API
  ↓
返回结果
```

---

### Phase 3: 实现核心组件
**状态**: `pending`
**目标**: 实现参数收集流程的核心组件

**任务列表**:
1. **创建 ParamCollectionSession 实体**
   - sessionId, actionId, state, collectedParams, missingParams
   - createdAt, updatedAt
   - 状态枚举：INIT, COLLECTING, PENDING_CONFIRM, CONFIRMED, EXECUTING, COMPLETED

2. **实现 ParamCollectionService**
   - 创建/获取/更新会话
   - 参数提取和验证
   - 追问生成
   - 确认卡片生成
   - 会话持久化（内存/Redis）

3. **实现 StructuredParamExtractor**
   - 使用 LLM 从用户输入提取参数
   - Prompt 模板设计
   - 置信度跟踪

4. **实现 ParameterValidator**
   - 必填项检查
   - 类型验证
   - 格式验证（pattern, enum, range）
   - 缺失参数列表生成

5. **实现 ActionExecutor**
   - HTTP API 调用
   - 结果解析
   - 错误处理

---

### Phase 4: 集成到现有流程
**状态**: `pending`
**目标**: 将参数收集集成到 ActionIntentEvaluator 和执行流程

**集成点**:
1. 修改 `ActionIntentEvaluator.evaluate()`
   - 匹配到 action 后，检查是否需要参数收集
   - 如果是，触发参数收集流程

2. 扩展 `PlanningEvaluationHook`
   - 在评估结果中注入参数收集状态
   - 生成 contextMetadata 返回给前端

3. 修改 `CodeactAgent`
   - 执行前检查参数是否已确认
   - 调用 ActionExecutor 执行

4. 创建 SSE Controller（可选）
   - 如果需要流式响应，参考 assistant-management 的 SSE 实现

---

### Phase 5: 测试和验证
**状态**: `pending`
**目标**: 测试完整的参数收集流程

**测试场景**:
1. **场景 1**: 完整参数
   - 输入："添加产品单位，名称为个"
   - 预期：直接生成确认卡片

2. **场景 2**: 缺失必填参数
   - 输入："添加产品单位"
   - 预期：追问"请输入单位名称"

3. **场景 3**: 多轮参数收集
   - 第一轮："添加产品单位"
   - 第二轮："个"
   - 预期：生成确认卡片

4. **场景 4**: 参数修改
   - 确认后修改参数
   - 预期：返回参数收集状态

5. **场景 5**: 取消操作
   - 预期：会话取消，清理状态

---

### Phase 6: 文档和示例
**状态**: `pending`
**目标**: 编写使用文档和示例

**交付物**:
1. 配置说明（权重、阈值）
2. Action Parameter Schema 定义规范
3. Prompt 模板示例
4. 集成指南

---

## 错误记录
| 错误 | 尝试 | 解决方案 |
|-------|---------|------------|
| - | - | - |

---

## 决策记录
| 日期 | 决策 | 原因 |
|-------|------|------|
| 2026-01-16 | 从"修复匹配问题"切换到"实现参数收集流程" | 用户的实际需求是完整的参数收集和确认流程，而不仅仅是匹配 |
