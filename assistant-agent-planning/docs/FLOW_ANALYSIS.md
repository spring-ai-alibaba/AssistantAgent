# AssistantAgent 会话到 Action 匹配流程分析

**文档版本**: 1.0
**创建日期**: 2026-01-17
**作者**: Assistant Agent Team

---

## 目录

- [1. 概述](#1-概述)
- [2. 完整流程图](#2-完整流程图)
- [3. 关键组件](#3-关键组件)
- [4. 详细流程说明](#4-详细流程说明)
- [5. 集成点分析](#5-集成点分析)
- [6. 当前问题识别](#6-当前问题识别)
- [7. 与企业平台集成的挑战](#7-与企业平台集成的挑战)

---

## 1. 概述

本文档详细分析了 AssistantAgent 从接收用户请求到匹配并执行 Action 的完整流程，特别关注 `assistant-agent-planning` 模块如何集成到 Evaluation Graph 中。

### 核心发现

**Evaluation Graph 是 AssistantAgent 的意图识别引擎**，它：
- 在 Agent 执行**之前**进行多维度评估
- 使用有向图（StateGraph）编排评估标准（Criteria）
- 通过 Evaluator 接口扩展评估能力
- 将评估结果注入到 LLM 上下文中，引导后续行为

### planning 模块的集成方式

`assistant-agent-planning` 通过以下方式集成到 AssistantAgent：

1. **PlanningEvaluationCriterionProvider** - 提供 Action 匹配评估标准
2. **ActionIntentEvaluator** - 实现评估逻辑，匹配 Action 并处理参数收集
3. **InputRoutingEvaluationHook** - 在 BEFORE_AGENT 阶段触发评估

---

## 2. 完整流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          用户请求："添加产品单位"                              │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CodeactAgent.invoke()                                                      │
│  - 接收 OverAllState 和 RunnableConfig                                      │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  BEFORE_AGENT Phase - Hooks 执行                                            │
│  - InputRoutingEvaluationHook.beforeAgent()                                 │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  EvaluationService.evaluate()                                               │
│  - 加载 EvaluationSuite ("input-routing-suite")                             │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  GraphBasedEvaluationExecutor.execute()                                     │
│  - 初始化 Graph 状态                                                         │
│  - 执行 CompiledGraph                                                        │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  StateGraph Execution (基于 graph-core)                                     │
│                                                                              │
│  START → [action_intent_match] → END                                         │
│         (唯一评估标准)                                                        │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CriterionEvaluationAction.execute()                                         │
│  - 执行单个 Criterion 节点                                                   │
│  - 调用对应的 Evaluator                                                      │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ActionIntentEvaluator.evaluate()                                            │
│  (Planning 模块的核心评估器)                                                  │
│                                                                              │
│  1. 获取 userInput 和 sessionId                                              │
│  2. 调用 ActionProvider.matchActions()                                       │
│     - SemanticActionProvider: 向量搜索 + 关键词匹配                          │
│     - 计算综合得分 = semanticScore * 0.6 + keywordScore * 0.4               │
│     - 过滤低于阈值(0.5)的匹配                                                │
│  3. 如果匹配成功：                                                            │
│     - 检查是否需要参数收集（enableParamCollection + 必填参数）                │
│     - 如果是：调用 ParamCollectionService                                    │
│     - 如果否：返回 MATCHED 结果                                              │
│  4. 如果未匹配：返回 NO_MATCH                                                │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
                     ┌────────┴────────┐
                     │                 │
                     ▼                 ▼
         ┌──────────────────┐  ┌──────────────────┐
         │  匹配成功        │  │  参数收集模式     │
         │  (NO_PARAM)      │  │  (PARAM_COLLECTION)│
         └────────┬─────────┘  └────────┬─────────┘
                  │                     │
                  │                     ▼
                  │         ┌──────────────────────┐
                  │         │ ParamCollectionService│
                  │         │ .processUserInput()   │
                  │         │ - 提取参数 (LLM)      │
                  │         │ - 验证参数            │
                  │         │ - 生成追问/确认       │
                  │         └──────────┬───────────┘
                  │                     │
                  │                     ▼
                  │         ┌──────────────────────┐
                  │         │ 返回参数收集状态      │
                  │         │ PARAM_COLLECTION|... │
                  │         └──────────┬───────────┘
                  │                     │
                  └──────────┬──────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CriterionResult 返回                                                        │
│  - value: "MATCHED|actionId|actionName|confidence|matchType"                │
│    或 "PARAM_COLLECTION|sessionId|actionId|state|message|..."              │
│  - metadata: { paramCollectionSessionId, actionId, state, ... }            │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  InputRoutingEvaluationHook.injectEvaluationResultToMessages()              │
│  - 构建 AssistantMessage (toolCall)                                          │
│  - 构建 ToolResponseMessage (evaluation result)                              │
│  - 返回 messages 更新                                                        │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  更新 OverAllState                                                           │
│  - messages: [..., assistantMessage, toolResponseMessage]                    │
│  - evaluationInputRoutingResult: { action_intent_match: {...} }              │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  AFTER_AGENT Phase                                                           │
│  - Agent 基于评估结果和 messages 生成响应                                     │
│  - 如果是 MATCHED: 可能直接调用工具或生成代码执行 Action                      │
│  - 如果是 PARAM_COLLECTION: 返回追问消息给用户                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 关键组件

### 3.1 CodeactAgent

**位置**: `assistant-agent-autoconfigure/src/main/java/com/alibaba/assistant/agent/autoconfigure/CodeactAgent.java`

**职责**:
- 继承 Spring AI Alibaba 的 `ReactAgent`
- 管理 CodeactTool 注册表
- 创建 GraalCodeExecutor 用于代码执行
- 注册 Hooks 和 Interceptors

**关键代码**:
```java
public class CodeactAgent extends ReactAgent {
    // CodeAct specific components
    private final CodeContext codeContext;
    private final RuntimeEnvironmentManager environmentManager;
    private final GraalCodeExecutor executor;

    // Hooks and Interceptors
    private List<Hook> subAgentHooks = new ArrayList<>();
    private List<Interceptor> interceptors = new ArrayList<>();
}
```

**构建流程** (CodeactAgentBuilder.build()):
1. 初始化 CodeactToolRegistry
2. 注册 CodeactTools
3. 创建 CodeactSubAgentInterceptor（代码生成拦截器）
4. 创建 AgentLlmNode（配置 systemPrompt, tools）
5. 创建 AgentToolNode（配置工具执行）
6. 设置 ModelInterceptors 和 ToolInterceptors
7. 返回 CodeactAgent 实例

---

### 3.2 InputRoutingEvaluationHook

**位置**: `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/evaluation/hook/InputRoutingEvaluationHook.java`

**职责**:
- 在 `BEFORE_AGENT` 阶段触发评估
- 构造评估上下文 (EvaluationContext)
- 调用 EvaluationService 执行评估
- 将评估结果注入到 messages 中

**关键方法**:
```java
@Override
public CompletableFuture<Map<String, Object>> beforeAgent(
        OverAllState state, RunnableConfig config) {

    // 1. 构造评估上下文
    EvaluationContext context = contextFactory.createInputRoutingContext(state, config);

    // 2. 加载评估套件
    EvaluationSuite suite = evaluationService.loadSuite(suiteId);

    // 3. 执行评估
    EvaluationResult result = evaluationService.evaluate(suite, context);

    // 4. 写入状态
    Map<String, Object> updates = resultAttacher.attachInputRoutingResult(state, result);

    // 5. 注入到 messages
    Map<String, Object> messageUpdates = injectEvaluationResultToMessages(state, result);

    return CompletableFuture.completedFuture(allUpdates);
}
```

**注入方式**:
使用 AssistantMessage + ToolResponseMessage 配对：
```
AssistantMessage: { toolCalls: [{ name: "input_routing_evaluation_injection", ... }] }
ToolResponseMessage: { responses: [{ name: "input_routing_evaluation_injection", content: "..." }] }
```

这样 LLM 可以看到评估结果，并在后续响应中基于这些信息做决策。

---

### 3.3 EvaluationService

**位置**: `assistant-agent-evaluation/src/main/java/com/alibaba/assistant/agent/evaluation/DefaultEvaluationService.java`

**职责**:
- 管理评估套件（EvaluationSuite）注册表
- 委托给 GraphBasedEvaluationExecutor 执行评估
- 支持同步和异步评估

**关键代码**:
```java
public class DefaultEvaluationService implements EvaluationService {
    private final GraphBasedEvaluationExecutor executor;
    private final Map<String, EvaluationSuite> suiteRegistry = new ConcurrentHashMap<>();

    @Override
    public EvaluationResult evaluate(EvaluationSuite suite, EvaluationContext context) {
        return executor.execute(suite, context);
    }
}
```

---

### 3.4 GraphBasedEvaluationExecutor

**位置**: `assistant-agent-evaluation/src/main/java/com/alibaba/assistant/agent/evaluation/executor/GraphBasedEvaluationExecutor.java`

**职责**:
- 使用 Spring AI Alibaba Graph Core 编译和执行 StateGraph
- 管理并行执行的线程池
- 处理依赖关系（dependsOn）

**执行流程**:
```java
public EvaluationResult execute(EvaluationSuite suite, EvaluationContext context) {
    // 1. 获取预编译的图
    CompiledGraph compiledGraph = suite.getCompiledGraph();

    // 2. 初始化状态数据
    Map<String, Object> initialData = new HashMap<>();
    initialData.put("suite", suite);
    initialData.put("evaluationContext", context);

    // 3. 配置并行节点执行器
    RunnableConfig config = configBuilder.build();

    // 4. 执行图
    Optional<NodeOutput> outputOpt = compiledGraph.invokeAndGetOutput(initialData, config);

    // 5. 收集结果
    Map<String, Object> finalStateData = outputOpt.get().state().data();
    for (EvaluationCriterion criterion : suite.getCriteria()) {
        String resultKey = criterion.getName() + "_result";
        CriterionResult result = (CriterionResult) finalStateData.get(resultKey);
        criteriaResults.put(criterion.getName(), result);
    }

    return result;
}
```

**图结构示例** (对于 input-routing-suite):
```
         START
           │
           ▼
    [action_intent_match]
           │
           ▼
          END
```

如果有多个 criteria 且有依赖关系：
```
         START
           │
           ├─→ [criterion_a]
           │        │
           │        ├─→ [criterion_b]
           │        │        │
           │        │        └─→ [criterion_d]
           │        │
           │        └─→ [criterion_c]
           │                 │
           │                 └─→ [criterion_e]
           │
           └─→ [criterion_f]
                    │
                    ▼
                   END
```

---

### 3.5 EvaluationSuite

**位置**: `assistant-agent-evaluation/src/main/java/com/alibaba/assistant/agent/evaluation/model/EvaluationSuite.java`

**职责**:
- 定义评估标准的集合
- 包含预编译的 CompiledGraph

**配置示例** (通过 EvaluationSuiteBuilder 创建):
```java
EvaluationSuite suite = EvaluationSuiteBuilder.create("input-routing-suite")
    .description("输入路由评估套件")
    .addCriteria(
        // action_intent_match criterion 由 PlanningEvaluationCriterionProvider 提供
    )
    .build();
```

---

### 3.6 EvaluationCriterion

**位置**: `assistant-agent-evaluation/src/main/java/com/alibaba/assistant/agent/evaluation/model/EvaluationCriterion.java`

**职责**:
- 定义单个评估标准
- 包含 evaluator 引用、配置、依赖关系

**关键字段**:
```java
public class EvaluationCriterion {
    private String name;                    // 评估标准名称（唯一标识）
    private String description;             // 描述
    private ResultType resultType;          // 结果类型（TEXT, BOOLEAN, ENUM, JSON, SCORE）
    private List<String> options;           // 枚举选项（当 resultType=ENUM）
    private List<String> dependsOn;         // 依赖的其他评估标准
    private String evaluatorRef;            // 评估器引用（evaluator ID）
    private Map<String, Object> config;     // 评估器配置
    private String workingMechanism;        // LLM 工作机制描述
    private List<FewShotExample> fewShots;  // Few-shot 示例
    private ReasoningPolicy reasoningPolicy;// 推理策略
    private String customPrompt;            // 自定义提示（覆盖默认）
    private List<String> contextBindings;   // 上下文字段绑定
    private CriterionBatchingConfig batchingConfig; // 批处理配置
}
```

---

### 3.7 Evaluator 接口

**位置**: `assistant-agent-evaluation/src/main/java/com/alibaba/assistant/agent/evaluation/evaluator/Evaluator.java`

**职责**:
- 定义评估器的标准接口
- 所有评估器必须实现此接口

**接口定义**:
```java
public interface Evaluator {
    /**
     * 评估一个标准
     */
    CriterionResult evaluate(CriterionExecutionContext executionContext);

    /**
     * 获取评估器唯一标识
     */
    String getEvaluatorId();
}
```

**实现类型**:
1. **LLMBasedEvaluator** - 基于 LLM 的评估
2. **RuleBasedEvaluator** - 基于规则的评估
3. **ActionIntentEvaluator** - Action 匹配评估（Planning 模块）

---

### 3.8 PlanningEvaluationCriterionProvider

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/evaluation/PlanningEvaluationCriterionProvider.java`

**职责**:
- 实现 `EvaluationCriterionProvider` 接口
- 向 evaluation suite 提供 `action_intent_match` 评估标准
- 注册 ActionIntentEvaluator 到 EvaluatorRegistry

**关键代码**:
```java
@Component
public class PlanningEvaluationCriterionProvider implements EvaluationCriterionProvider {

    @PostConstruct
    public void init() {
        // 注册 ActionIntentEvaluator
        ActionIntentEvaluator evaluator = new ActionIntentEvaluator(actionProvider);
        evaluatorRegistry.registerEvaluator(evaluator);
    }

    @Override
    public List<EvaluationCriterion> getCodeActPhaseCriteria() {
        return List.of(createActionIntentCriterion());
    }

    private EvaluationCriterion createActionIntentCriterion() {
        return EvaluationCriterionBuilder.create("action_intent_match")
                .description("评估用户输入是否匹配预定义动作")
                .resultType(ResultType.JSON)
                .evaluatorRef("action_intent_evaluator")
                .build();
    }
}
```

---

### 3.9 ActionIntentEvaluator

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/evaluation/ActionIntentEvaluator.java`

**职责**:
- 实现 Evaluator 接口
- 匹配用户输入到预定义的 Action
- 可选地触发参数收集流程

**核心逻辑**:
```java
@Override
public CriterionResult evaluate(CriterionExecutionContext executionContext) {
    // 1. 获取用户输入
    String userInput = (String) inputContext.getInputValue("userInput");
    String sessionId = (String) inputContext.getInputValue("sessionId");
    String userId = (String) inputContext.getInputValue("userId");

    // 2. 执行动作匹配
    List<ActionMatch> matches = actionProvider.matchActions(userInput, context);

    // 3. 未匹配
    if (matches.isEmpty()) {
        result.setValue("NO_MATCH");
        return result;
    }

    // 4. 匹配到最佳动作
    ActionMatch bestMatch = matches.get(0);
    ActionDefinition action = bestMatch.getAction();

    // 5. 如果启用参数收集且动作需要参数
    if (enableParamCollection && needsParamCollection(action)) {
        return handleParamCollection(action, userInput, sessionId, userId, result);
    }

    // 6. 直接返回匹配结果
    result.setValue(String.format("MATCHED|%s|%s|%.2f|%s",
        action.getActionId(), action.getActionName(),
        bestMatch.getConfidence(), bestMatch.getMatchType()));
    return result;
}
```

**参数收集流程**:
```java
private CriterionResult handleParamCollection(...) {
    // 1. 检查是否已有活跃会话
    ParamCollectionSession existingSession =
        paramCollectionService.getActiveSessionByAssistantSessionId(sessionId);

    // 2. 创建或恢复会话
    ParamCollectionSession session = existingSession != null
        ? existingSession
        : paramCollectionService.createSession(action, sessionId, userId);

    // 3. 处理用户输入
    ProcessResult processResult =
        paramCollectionService.processUserInput(session, action, userInput, null);

    // 4. 构建返回结果
    result.setValue(String.format("PARAM_COLLECTION|%s|%s|%s|%s|%b|%b",
        session.getSessionId(), action.getActionId(), session.getState(),
        processResult.getMessage(), processResult.isRequiresInput(),
        processResult.isRequiresConfirmation()));

    // 5. 添加元数据（供前端使用）
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("paramCollectionSessionId", session.getSessionId());
    metadata.put("actionId", action.getActionId());
    metadata.put("state", session.getState().name());
    metadata.put("requiresInput", processResult.isRequiresInput());
    metadata.put("requiresConfirmation", processResult.isRequiresConfirmation());
    result.setMetadata(metadata);

    return result;
}
```

---

### 3.10 SemanticActionProvider

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/SemanticActionProvider.java`

**职责**:
- 实现 ActionProvider 接口
- 提供混合搜索能力（向量搜索 + 关键词匹配）
- 从数据库加载 Action 定义

**匹配逻辑**:
```java
@Override
public List<ActionMatch> matchActions(String userInput, Map<String, Object> context) {
    // 1. 向量语义搜索
    List<VectorSearchResult> semanticResults = vectorService.hybridSearch(userInput, topK);

    // 2. 关键词匹配（作为补充）
    Map<String, Double> keywordScores = computeKeywordScores(userInput);

    // 3. 融合结果
    Map<String, Double> combinedScores = new HashMap<>();
    for (VectorSearchResult result : semanticResults) {
        combinedScores.put(result.getActionId(),
            result.getScore() * semanticWeight);  // 默认 0.6
    }
    for (Map.Entry<String, Double> entry : keywordScores.entrySet()) {
        combinedScores.merge(entry.getKey(),
            entry.getValue() * keywordWeight,   // 默认 0.4
            Double::sum);
    }

    // 4. 过滤低于阈值的结果（默认 0.5）
    List<ActionMatch> matches = combinedScores.entrySet().stream()
        .filter(e -> e.getValue() >= threshold)
        .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
        .map(e -> createActionMatch(e.getKey(), e.getValue()))
        .collect(Collectors.toList());

    return matches;
}
```

**问题**: 阈值默认 0.5，但关键词匹配最高只能到 0.38（0.95 * 0.4），导致"添加单位"匹配失败。

---

### 3.11 ParamCollectionService

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/service/ParamCollectionService.java`

**职责**:
- 管理参数收集会话的生命周期
- 调用 StructuredParamExtractor 提取参数
- 调用 ParameterValidator 验证参数
- 生成追问消息或确认卡片

**会话存储**:
```java
@Service
public class ParamCollectionService {
    // ❌ 问题：内存存储，重启丢失
    private final Map<String, ParamCollectionSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> assistantSessionIndex = new ConcurrentHashMap<>();
}
```

**处理流程**:
```java
public ProcessResult processUserInput(
        ParamCollectionSession session,
        ActionDefinition action,
        String userInput,
        List<String> chatHistory) {

    // 1. 提取参数（使用 LLM）
    Map<String, Object> extractedParams =
        paramExtractor.extractParameters(action, userInput, chatHistory, session);

    // 2. 验证参数
    ValidationResult validation = validator.validate(action, extractedParams);

    // 3. 检查是否完成
    if (validation.isValid() && validation.getMissingParams().isEmpty()) {
        // 所有参数收集完成，生成确认卡片
        return generateConfirmation(session, action);
    } else {
        // 生成追问消息
        return generatePromptForMissingParams(session, validation.getMissingParams());
    }
}
```

---

## 4. 详细流程说明

### 4.1 请求入口

**HTTP 请求示例**:
```
POST /api/agent/chat
{
  "sessionId": "session-123",
  "userId": "user-456",
  "message": "添加产品单位"
}
```

**Controller 处理**:
```java
@PostMapping("/chat")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    OverAllState state = new OverAllState();
    state.put("sessionId", request.getSessionId());
    state.put("userId", request.getUserId());
    state.put("messages", List.of(new UserMessage(request.getMessage())));

    // 调用 CodeactAgent
    Map<String, Object> response = codeactAgent.invoke(state, config);

    return ResponseEntity.ok(new ChatResponse(response));
}
```

---

### 4.2 Hook 执行阶段

**BEFORE_AGENT 阶段**:

1. **InputRoutingEvaluationHook.beforeAgent()** 被调用
2. 构造 EvaluationContext:
   ```java
   EvaluationContext context = new EvaluationContext();
   context.setInputValue("userInput", "添加产品单位");
   context.setInputValue("sessionId", "session-123");
   context.setInputValue("userId", "user-456");
   ```
3. 加载 EvaluationSuite:
   ```java
   EvaluationSuite suite = evaluationService.loadSuite("input-routing-suite");
   ```
4. 执行评估:
   ```java
   EvaluationResult result = evaluationService.evaluate(suite, context);
   ```

---

### 4.3 评估执行阶段

**GraphBasedEvaluationExecutor.execute()**:

1. 获取预编译的图:
   ```java
   CompiledGraph compiledGraph = suite.getCompiledGraph();
   ```

2. 初始化状态:
   ```java
   Map<String, Object> initialData = new HashMap<>();
   initialData.put("suite", suite);
   initialData.put("evaluationContext", context);
   ```

3. 执行图:
   ```java
   Optional<NodeOutput> output = compiledGraph.invokeAndGetOutput(initialData, config);
   ```

4. 图节点执行流程:
   - **START** → 调用 `CriterionEvaluationAction.execute("action_intent_match")`
   - **CriterionEvaluationAction** → 调用 `ActionIntentEvaluator.evaluate()`
   - **END** → 返回最终状态

5. 收集结果:
   ```java
   CriterionResult result = (CriterionResult) finalStateData.get("action_intent_match_result");
   ```

---

### 4.4 Action 匹配阶段

**ActionIntentEvaluator.evaluate()**:

1. **获取用户输入**:
   ```java
   String userInput = "添加产品单位";
   String sessionId = "session-123";
   String userId = "user-456";
   ```

2. **构建匹配上下文**:
   ```java
   Map<String, Context> context = new HashMap<>();
   context.putAll(evalContext.getInput());
   context.putAll(evalContext.getEnvironment());
   ```

3. **调用 ActionProvider**:
   ```java
   List<ActionMatch> matches = actionProvider.matchActions(userInput, context);
   ```

4. **SemanticActionProvider.matchActions()**:
   ```java
   // 向量搜索
   List<VectorSearchResult> semanticResults = vectorService.hybridSearch("添加产品单位", 10);

   // 关键词匹配
   Map<String, Double> keywordScores = computeKeywordScores("添加产品单位");
   // 结果: {"erp:product-unit:create": 0.95}

   // 融合得分
   double combinedScore = semanticScore * 0.6 + keywordScore * 0.4;
   // 结果: 0.0 * 0.6 + 0.95 * 0.4 = 0.38

   // 过滤阈值
   if (combinedScore >= 0.5) {  // ❌ 0.38 < 0.5，匹配失败
       matches.add(...);
   }
   ```

5. **返回结果**:
   - 如果 matches 为空: `result.setValue("NO_MATCH")`
   - 如果有匹配:
     - 检查是否需要参数收集
     - 如果启用: `handleParamCollection()`
     - 如果不启用: `result.setValue("MATCHED|...")`

---

### 4.5 参数收集阶段（如果启用）

**ActionIntentEvaluator.handleParamCollection()**:

1. **检查现有会话**:
   ```java
   ParamCollectionSession existingSession =
       paramCollectionService.getActiveSessionByAssistantSessionId("session-123");
   ```

2. **创建新会话** (如果不存在):
   ```java
   ParamCollectionSession session = paramCollectionService.createSession(
       action,    // erp:product-unit:create
       sessionId, // session-123
       userId     // user-456
   );
   // session.state = COLLECTING
   // session.collectedParams = {}
   ```

3. **处理用户输入**:
   ```java
   ProcessResult processResult = paramCollectionService.processUserInput(
       session,
       action,
       "添加产品单位",
       null  // chatHistory
   );
   ```

4. **ParamCollectionService.processUserInput()**:
   ```java
   // 提取参数
   Map<String, Object> extractedParams = paramExtractor.extractParameters(
       action,
       "添加产品单位",
       null,
       session
   );
   // 结果: {} (没有提取到参数)

   // 验证参数
   ValidationResult validation = validator.validate(action, extractedParams);
   // 结果: missingParams = [ActionParameter(name="name", required=true)]

   // 生成追问
   return ProcessResult.builder()
       .requiresInput(true)
       .message("请输入单位名称（计量单位名称，如：个、台、箱、件等）")
       .missingParams(validation.getMissingParams())
       .build();
   ```

5. **构建返回结果**:
   ```java
   result.setValue("PARAM_COLLECTION|abc-123-def|erp:product-unit:create|COLLECTING|请输入单位名称|true|false");

   Map<String, Object> metadata = new HashMap<>();
   metadata.put("paramCollectionSessionId", "abc-123-def");
   metadata.put("actionId", "erp:product-unit:create");
   metadata.put("actionName", "添加产品单位");
   metadata.put("state", "COLLECTING");
   metadata.put("requiresInput", true);
   metadata.put("requiresConfirmation", false);
   metadata.put("completed", false);
   metadata.put("message", "请输入单位名称");
   result.setMetadata(metadata);
   ```

---

### 4.6 结果注入阶段

**InputRoutingEvaluationHook.injectEvaluationResultToMessages()**:

1. **获取现有 messages**:
   ```java
   List<Message> messages = state.value("messages");
   // [UserMessage("添加产品单位")]
   ```

2. **检查是否已注入**:
   ```java
   // 遍历 messages，查找是否已有 input_routing_evaluation_injection
   // 如果有，跳过重复注入
   ```

3. **构建评估内容**:
   ```java
   String content = "=== 输入路由评估结果 ===\n\n" +
       "🔍 action_intent_match: PARAM_COLLECTION|abc-123-def|...\n";
   ```

4. **构造消息配对**:
   ```java
   String toolCallId = "eval_input_" + UUID.randomUUID();

   AssistantMessage assistantMessage = AssistantMessage.builder()
       .toolCalls(List.of(
           new AssistantMessage.ToolCall(
               toolCallId,
               "function",
               "input_routing_evaluation_injection",
               "{}"
           )
       ))
       .build();

   ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
       .responses(List.of(
           new ToolResponseMessage.ToolResponse(
               toolCallId,
               "input_routing_evaluation_injection",
               content
           )
       ))
       .build();
   ```

5. **返回更新**:
   ```java
   return Map.of("messages", List.of(assistantMessage, toolResponseMessage));
   ```

---

### 4.7 Agent 响应阶段

**AFTER_AGENT 阶段**:

1. **LLM 接收到评估结果**:
   ```
   messages: [
       UserMessage("添加产品单位"),
       AssistantMessage({toolCalls: [...]})
       ToolResponseMessage("=== 输入路由评估结果 ===\n...")
   ]
   ```

2. **LLM 生成响应**:
   - LLM 看到评估结果中有 `requiresInput: true`
   - LLM 看到消息："请输入单位名称"
   - LLM 生成响应："请输入单位名称（计量单位名称，如：个、台、箱、件等）"

3. **返回给用户**:
   ```json
   {
     "message": "请输入单位名称（计量单位名称，如：个、台、箱、件等）",
     "metadata": {
       "paramCollectionSessionId": "abc-123-def",
       "requiresInput": true
     }
   }
   ```

4. **用户继续输入**:
   ```
   User: "个"
   ```

5. **下一轮评估**:
   - InputRoutingEvaluationHook 再次执行
   - ActionIntentEvaluator 找到现有会话 (abc-123-def)
   - processUserInput() 提取到参数 `{name: "个"}`
   - 验证通过，生成确认卡片
   - 返回 `PARAM_COLLECTION|...|PENDING_CONFIRM|请确认以下信息|false|true`

6. **用户确认**:
   ```
   User: "确认"
   ```

7. **执行 Action**:
   - processUserInput() 识别到确认意图
   - 调用 ActionExecutor.execute()
   - 执行 HTTP API: `POST https://api.simplify.devefive.com/.../product-unit/create`
   - 返回执行结果

---

## 5. 集成点分析

### 5.1 Planning 模块的集成点

**集成点 1: EvaluationCriterionProvider 接口**
- **位置**: `PlanningEvaluationCriterionProvider`
- **触发时机**: Spring 启动时，通过 `@PostConstruct` 注册 evaluator
- **作用**: 向 evaluation suite 提供 `action_intent_match` 评估标准

**集成点 2: Evaluator 接口**
- **位置**: `ActionIntentEvaluator`
- **触发时机**: Evaluation Graph 执行到 `action_intent_match` 节点
- **作用**: 执行 Action 匹配和参数收集逻辑

**集成点 3: ActionProvider SPI**
- **位置**: `SemanticActionProvider`
- **触发时机**: ActionIntentEvaluator 调用 `matchActions()`
- **作用**: 提供动作匹配能力（向量搜索 + 关键词匹配）

**集成点 4: InputRoutingEvaluationHook**
- **位置**: `assistant-agent-extensions/evaluation/hook`
- **触发时机**: BEFORE_AGENT 阶段
- **作用**: 触发评估并注入结果到 messages

---

### 5.2 数据流分析

```
用户输入
   ↓
OverAllState
   ↓
InputRoutingEvaluationHook
   ↓
EvaluationContext (输入映射)
   ↓
GraphBasedEvaluationExecutor
   ↓
CriterionEvaluationAction
   ↓
ActionIntentEvaluator
   ↓
ActionProvider (SemanticActionProvider)
   ↓
ParamCollectionService (如果启用)
   ↓
CriterionResult (返回值 + 元数据)
   ↓
InputRoutingEvaluationHook (注入到 messages)
   ↓
OverAllState (更新)
   ↓
LLM (基于 messages 生成响应)
   ↓
用户看到最终结果
```

---

### 5.3 上下文传递

**EvaluationContext** (评估上下文):
```java
EvaluationContext context = new EvaluationContext();
context.setInputValue("userInput", "添加产品单位");
context.setInputValue("sessionId", "session-123");
context.setInputValue("userId", "user-456");
context.setInput(Map.of("message", "添加产品单位"));
context.setEnvironment(Map.of("tenantId", 1, "systemId", 1));
```

**CriterionExecutionContext** (评估标准执行上下文):
```java
CriterionExecutionContext executionContext = new CriterionExecutionContext();
executionContext.setCriterion(criterion);  // action_intent_match
executionContext.setInputContext(context); // EvaluationContext
executionContext.setSuite(suite);          // input-routing-suite
```

**OverAllState** (Agent 状态):
```java
OverAllState state = new OverAllState();
state.put("sessionId", "session-123");
state.put("userId", "user-456");
state.put("messages", List.of(...));
state.put("evaluationInputRoutingResult", Map.of(
    "action_intent_match", CriterionResult{...}
));
```

---

## 6. 当前问题识别

### 6.1 架构层面的问题

**问题 1: 与 Evaluation 模块紧耦合**
- **现象**: `ActionIntentEvaluator` 直接实现 `Evaluator` 接口
- **影响**:
  - planning 模块无法独立于 evaluation 模块使用
  - 强依赖 evaluation 的数据结构（CriterionResult, EvaluationContext）
  - 违反了模块解耦原则
- **根本原因**: 为了快速集成，直接使用了 evaluation 的扩展点

**问题 2: 评估结果格式不规范**
- **现象**: 使用字符串拼接传递结果: `"MATCHED|actionId|actionName|confidence|matchType"`
- **影响**:
  - 解析复杂，容易出错
  - 不支持嵌套结构
  - 缺乏类型安全
- **建议**: 使用结构化 JSON 或专门的 Result 对象

**问题 3: 参数收集流程嵌入在评估流程中**
- **现象**: `handleParamCollection()` 在 `ActionIntentEvaluator.evaluate()` 中调用
- **影响**:
  - 评估逻辑和参数收集逻辑混在一起
  - 难以单独测试和复用
  - 违反单一职责原则
- **建议**: 将参数收集作为独立的 Hook 或 Interceptor

---

### 6.2 数据层面的问题

**问题 4: ActionDefinition 缺少租户字段**
- **现象**: 没有 `tenantId`, `systemId`, `moduleId` 字段
- **影响**:
  - 无法实现多租户数据隔离
  - 所有租户共享同一套 Action 定义
  - 不符合企业平台需求
- **解决方案**: 扩展数据模型，添加租户字段（已在 REDESIGN_PROPOSAL.md 中设计）

**问题 5: 缺少权限检查机制**
- **现象**: `requiredPermissions` 字段存在但未使用
- **影响**:
  - 任何用户都可以执行任何 Action
  - 存在安全风险
- **解决方案**: 实现 PermissionProvider SPI，在评估后、执行前检查权限

**问题 6: 会话存储在内存中**
- **现象**: `ParamCollectionService` 使用 `ConcurrentHashMap` 存储会话
- **影响**:
  - 应用重启后所有会话丢失
  - 无法支持分布式部署
  - 无法实现会话持久化和恢复
- **解决方案**: 实现 SessionProvider SPI，支持 Redis/MySQL 存储

---

### 6.3 功能层面的问题

**问题 7: Action 匹配阈值不合理**
- **现象**: 默认阈值 0.5，但关键词匹配最高只能到 0.38
- **影响**:
  - "添加单位"等简单指令无法匹配
  - 用户体验差
- **临时方案**:
  ```yaml
  spring:
    ai:
      alibaba:
        codeact:
          extension:
            planning:
              matching:
                threshold: 0.3  # 降低阈值
                keyword-weight: 0.6  # 提高关键词权重
  ```
- **根本方案**: 优化匹配算法，支持可配置的阈值策略

**问题 8: MCP 执行器未实现**
- **现象**: `ActionExecutor.McpExecutor` 返回 "MCP 执行器尚未实现"
- **影响**:
  - 无法集成 MCP 工具（包括 DataAgent）
  - HTTP API 是唯一可用的执行方式
- **解决方案**: 实现 McpExecutor，调用 MCP Server

**问题 9: 参数提取依赖 LLM**
- **现象**: `StructuredParamExtractor` 使用 LLM 提取参数
- **影响**:
  - 每次提取都需要调用 LLM，延迟高
  - 成本高（token 消耗）
  - 对于简单的参数（如单个字符串），过于复杂
- **优化方案**:
  - 对于简单参数，使用规则提取（正则表达式）
  - 对于复杂参数，使用 LLM 提取
  - 支持混合策略

---

### 6.4 配置层面的问题

**问题 10: 配置项分散**
- **现象**: `PlanningExtensionProperties` 包含多个嵌套配置类
- **影响**:
  - 配置复杂，容易出错
  - 缺少默认值和验证
  - 文档不完善
- **解决方案**: 简化配置结构，提供配置模板和验证

**问题 11: 自动配置条件不清晰**
- **现象**: `ParamCollectionAutoConfiguration` 的条件判断复杂
- **影响**:
  - 用户不知道如何正确启用参数收集
  - 配置错误时难以排查
- **解决方案**: 提供清晰的配置指南和错误提示

---

### 6.5 测试层面的问题

**问题 12: 缺少单元测试**
- **现象**: 核心组件没有对应的测试类
- **影响**:
  - 代码质量难以保证
  - 重构风险高
  - 回归测试困难
- **解决方案**: 补充单元测试，覆盖率达到 60%+

**问题 13: 缺少集成测试**
- **现象**: 没有端到端的测试场景
- **影响**:
  - 无法验证完整流程
  - 集成问题难以发现
- **解决方案**: 编写集成测试，覆盖典型场景

---

## 7. 与企业平台集成的挑战

### 7.1 多租户隔离

**挑战**: 当前实现不支持租户级别的数据隔离

**现状**:
- ActionDefinition 是全局的
- ActionProvider.matchActions() 不过滤租户
- ParamCollectionSession 不关联租户

**需求**:
- 不同租户可以定义不同的 Action
- 同一个 Action 名称在不同租户下可以有不同的配置
- 参数收集会话需要隔离到租户

**解决方案** (参考 REDESIGN_PROPOSAL.md):
1. 扩展 ActionDefinition 添加租户字段
2. 实现 TenantContext 管理租户上下文
3. 在 ActionProvider 中添加租户过滤逻辑
4. 在 ParamCollectionService 中添加租户隔离

---

### 7.2 权限管理

**挑战**: 当前实现没有权限检查机制

**现状**:
- ActionDefinition 有 `requiredPermissions` 字段但未使用
- 任何用户都可以执行任何 Action
- 没有角色和权限的概念

**需求**:
- 基于角色的访问控制（RBAC）
- 支持数据权限（行级安全）
- 细粒度的权限控制（操作级别）

**解决方案** (参考 REDESIGN_PROPOSAL.md):
1. 实现 PermissionProvider SPI
2. 在 ActionIntentEvaluator 和 ActionExecutor 之间添加权限检查
3. 扩展 ActionDefinition 添加 `allowedRoles` 和数据权限配置
4. 提供权限管理 API

---

### 7.3 DataAgent 集成

**挑战**: DataAgent 需要通过 MCP 或 Tool 方式集成

**现状**:
- MCP 执行器未实现
- 不支持外部 MCP Server 调用
- DataAgent 需要作为独立的 MCP Server 部署

**需求**:
- DataAgent 作为 MCP Server 提供数据查询和分析能力
- AssistantAgent 可以调用 DataAgent 的工具
- 上下文（租户、用户）需要在调用间传递

**解决方案** (参考 REDESIGN_PROPOSAL.md):
1. 实现 McpExecutor
2. 支持 MCP Server 配置和注册
3. 实现 DataAgent MCP 客户端
4. 在参数中传递租户上下文

---

### 7.4 可扩展性

**挑战**: 当前实现的扩展点有限

**现状**:
- 只支持 HTTP API 执行
- 参数收集流程硬编码
- 缺少插件机制

**需求**:
- 支持自定义 ActionExecutor
- 支持自定义参数收集策略
- 支持自定义验证规则

**解决方案** (参考 REDESIGN_PROPOSAL.md):
1. 将 ActionExecutor 改为 SPI
2. 使用工厂模式管理执行器
3. 提供 Strategy 接口用于参数收集
4. 提供 ValidatorRegistry 用于注册自定义验证器

---

### 7.5 性能和可靠性

**挑战**: 企业级应用对性能和可靠性有更高要求

**现状**:
- 参数提取每次都调用 LLM
- 会话存储在内存中
- 没有缓存机制
- 没有限流和熔断

**需求**:
- 降低 LLM 调用次数
- 支持分布式会话存储
- 实现缓存机制
- 添加限流和熔断

**解决方案**:
1. 实现参数提取缓存
2. 实现 SessionProvider SPI（Redis/MySQL）
3. 添加 Caffeine 本地缓存
4. 集成 Resilience4j 实现限流和熔断

---

### 7.6 运维和监控

**挑战**: 企业级应用需要完善的运维和监控能力

**现状**:
- 日志不规范
- 没有指标采集
- 没有链路追踪
- 错误处理不完善

**需求**:
- 结构化日志
- Prometheus 指标
- 分布式追踪（SkyWalking/Zipkin）
- 完善的错误处理和重试机制

**解决方案**:
1. 统一日志格式（JSON）
2. 添加 Micrometer 指标
3. 集成 OpenTelemetry
4. 实现统一的异常处理和重试策略

---

## 8. 总结

### 8.1 核心流程回顾

AssistantAgent 的 Action 匹配流程如下：

```
用户请求 → CodeactAgent → BEFORE_AGENT Hook →
EvaluationService → GraphBasedEvaluationExecutor →
CriterionEvaluationAction → ActionIntentEvaluator →
ActionProvider → Action 匹配 →
(可选) ParamCollectionService →
CriterionResult → InputRoutingEvaluationHook (注入到 messages) →
LLM 生成响应 → 用户看到结果
```

**关键点**:
1. **Evaluation Graph 是核心**: 在 Agent 执行之前进行多维度评估
2. **Hook 是集成点**: BEFORE_AGENT Hook 触发评估
3. **Evaluator 是执行单元**: 每个评估标准对应一个 Evaluator
4. **结果注入到 messages**: 通过 AssistantMessage + ToolResponseMessage 配对方式
5. **LLM 基于评估结果生成响应**: 评估结果引导 LLM 的决策

---

### 8.2 Planning 模块的集成方式

**优点**:
- ✅ 利用 Evaluation Graph 的依赖管理能力
- ✅ 统一的评估结果格式
- ✅ 支持并行评估（多个 criteria）
- ✅ 结果注入机制完善

**缺点**:
- ❌ 与 Evaluation 模块紧耦合
- ❌ 无法独立使用
- ❌ 评估结果格式不规范（字符串拼接）
- ❌ 参数收集流程嵌入在评估流程中

---

### 8.3 与企业平台的差距

| 维度 | 当前实现 | 企业平台需求 | 差距 |
|------|---------|-------------|------|
| 多租户 | ❌ 不支持 | ✅ 三级隔离（租户/系统/模块） | 需扩展数据模型和上下文管理 |
| 权限 | ❌ 未实现 | ✅ RBAC + 数据权限 | 需实现 PermissionProvider SPI |
| DataAgent | ❌ 未集成 | ✅ MCP 集成 | 需实现 McpExecutor |
| 扩展性 | ⚠️ 有限 | ✅ 高度可扩展 | 需重构为 SPI 模式 |
| 会话存储 | ❌ 内存 | ✅ 持久化 | 需实现 SessionProvider SPI |
| 性能 | ⚠️ LLM 调用频繁 | ✅ 高性能 | 需优化和缓存 |
| 监控 | ⚠️ 基础日志 | ✅ 完善监控 | 需添加指标和追踪 |

---

### 8.4 下一步行动

1. **确认重新架构方案** (REDESIGN_PROPOSAL.md)
   - 三层模块结构（api/core/integration）
   - SPI 接口设计（SessionProvider, PermissionProvider, ActionExecutor）
   - 租户上下文管理（TenantContext）

2. **实施重新架构**
   - Phase 1-3: SPI 设计 + 数据模型 + 租户上下文
   - Phase 4-5: SessionProvider + PermissionProvider
   - Phase 6-7: ActionExecutor 重构 + MCP 实现
   - Phase 8: 集成层重构
   - Phase 9-10: 单元测试 + 集成测试
   - Phase 11-12: 文档 + 发布

3. **集成 DataAgent**
   - 部署 DataAgent 作为 MCP Server
   - 实现 MCP 客户端
   - 配置工具映射
   - 测试端到端流程

4. **完善监控和运维**
   - 添加 Prometheus 指标
   - 集成 OpenTelemetry
   - 实现结构化日志
   - 添加限流和熔断

---

**文档结束**
