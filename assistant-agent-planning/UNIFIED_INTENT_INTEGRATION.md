# 统一意图识别整合方案

## 概述

成功实现了 **Planning模块** 和 **Experience模块** 的整合，采用 **Hook + 评估模块** 的两层架构。

## 架构设计

```
用户输入
   ↓
UnifiedIntentRecognitionHook (BEFORE_AGENT)
   ↓
├─ 关键词快速过滤 (KeywordMatcher)
├─ 语义匹配 (ActionProvider)
   ↓
   置信度 >= 0.95 (高置信度) → Hook层处理
   │  ├─ 检查 Experience
   │  │  ├─ 有 Experience → FastIntent 快速执行 → END
   │  │  └─ 无 Experience → Planning 直接执行 → END
   │
   置信度 < 0.95 → 放行到评估模块
      ↓
   Evaluation Graph
      ├─ ActionIntentEvaluator (动作匹配评估)
      ↓
   ActionIntentPromptBuilder (提示构建)
      ├─ 0.85 <= 置信度 < 0.95 → 强烈建议使用 plan_action
      ├─ 0.7 <= 置信度 < 0.85 → 建议使用或确认
      └─ 置信度 < 0.7 → 不注入提示
      ↓
   CodeactAgent / ReactAgent (LLM决策)
```

## 核心组件

### 1. UnifiedIntentRecognitionHook

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/intent/UnifiedIntentRecognitionHook.java`

**职责**:
- 高置信度（>=0.95）动作的快速识别和执行
- 整合 Planning 和 Experience 模块
- 可跳过 LLM 调用（JumpTo.end / JumpTo.tool）

**关键代码**:
```java
if (confidence >= directExecuteThreshold) {
    // 高置信度：检查 Experience，决定执行方式（Hook层处理）
    return handleHighConfidence(bestMatch, userInput, context, state, config);
} else {
    // 中低置信度（<0.95）：放行到评估模块
    return CompletableFuture.completedFuture(Map.of());
}
```

### 2. ActionIntentEvaluator

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/evaluation/ActionIntentEvaluator.java`

**职责**:
- 在评估阶段执行动作匹配
- 返回 JSON 格式的匹配结果

**返回格式**:
```json
{
  "matched": true,
  "actionId": "erp:product-unit:create",
  "actionName": "添加产品单位",
  "confidence": 0.85,
  "parameters": {...},
  "missingParameters": [...]
}
```

### 3. ActionIntentPromptBuilder

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/evaluation/ActionIntentPromptBuilder.java`

**职责**:
- 根据评估结果生成提示
- 处理中等置信度（0.7-0.95）的匹配

**注入提示示例**:
```
【系统提示 - 检测到预定义动作】
根据用户输入，检测到可能匹配以下预定义动作：

- 动作ID: erp:product-unit:create
- 动作名称: 添加产品单位
- 置信度: 0.85
- 描述: 在ERP系统中创建新的产品计量单位

建议操作：
1. 置信度较高（>= 0.85），建议使用 plan_action 工具生成执行计划
2. 使用 execute_action 工具执行计划
```

### 4. PlanningEvaluationCriterionProvider

**位置**: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/evaluation/PlanningEvaluationCriterionProvider.java`

**职责**:
- 实现 `EvaluationCriterionProvider` 接口
- 注册 ActionIntentEvaluator 到 EvaluatorRegistry
- 提供 action_intent_match 评估标准

## 配置说明

### application.yml

```yaml
# Experience Module - 禁用 FastIntent 以避免冲突
spring.ai.alibaba.codeact.extension.experience:
  enabled: true
  fast-intent-enabled: false       # 已禁用
  fast-intent-react-enabled: false # 已禁用
  fast-intent-code-enabled: false  # 已禁用

# Planning Module
spring.ai.alibaba.codeact.extension.planning:
  enabled: true
  intent:
    enabled: true
    use-unified: true               # 启用统一Intent Hook
    direct-execute-threshold: 0.95  # 高置信度阈值（Hook层直接执行）
    hint-threshold: 0.7             # 中等置信度阈值（评估模块注入提示）

# Evaluation Module - 默认启用
spring.ai.alibaba.codeact.extension.evaluation:
  enabled: true  # 评估模块必须启用
```

## 执行流程

### 场景1: 高置信度（>= 0.95）+ 有 Experience

```
用户: "添加产品单位"
   ↓
UnifiedHook: 匹配到 erp:product-unit:create, confidence=0.98
   ↓
检查 Experience: 找到相关经验
   ↓
FastIntent: 直接生成 ToolCall
   ↓
跳转到 tool 节点执行
   ↓
返回结果 (跳过 LLM)
```

### 场景2: 高置信度（>= 0.95）+ 无 Experience

```
用户: "添加产品单位"
   ↓
UnifiedHook: 匹配到 erp:product-unit:create, confidence=0.98
   ↓
检查 Experience: 未找到经验
   ↓
Planning: 生成执行计划并执行
   ↓
返回结果 (跳过 LLM)
```

### 场景3: 中等置信度（0.7 - 0.95）

```
用户: "添加单位"
   ↓
UnifiedHook: 匹配到 erp:product-unit:create, confidence=0.85
   ↓
放行到评估模块
   ↓
Evaluation Graph: ActionIntentEvaluator 执行评估
   ↓
ActionIntentPromptBuilder: 注入提示
   ↓
LLM: 根据提示使用 plan_action 和 execute_action 工具
   ↓
返回结果
```

### 场景4: 低置信度（< 0.7）

```
用户: "加个东西"
   ↓
UnifiedHook: 匹配失败或置信度过低
   ↓
放行
   ↓
Evaluation Graph: ActionIntentEvaluator 评估（matched=false 或 confidence<0.7）
   ↓
ActionIntentPromptBuilder: 不注入提示（match返回false）
   ↓
LLM: 正常 ReAct 流程
   ↓
返回结果
```

## 关键优势

### 1. 性能优化
- **高置信度场景**: 跳过 LLM 调用，直接执行（~1s → ~50ms）
- **关键词过滤**: 快速排除不相关输入（<1ms）
- **语义匹配缓存**: Elasticsearch 向量搜索（~50ms）

### 2. 架构清晰
- **Hook层**: 处理确定性高的场景
- **评估模块**: 处理需要 LLM 辅助决策的场景
- **解耦设计**: Planning 和 Experience 模块独立

### 3. 可扩展性
- 添加新的评估标准: 实现 `EvaluationCriterionProvider`
- 添加新的提示策略: 实现 `PromptBuilder`
- 调整置信度阈值: 修改配置文件

## 测试步骤

### 1. 重新启动应用

```bash
cd assistant-agent-start
mvn spring-boot:run
```

### 2. 添加测试动作

```bash
# 使用之前的 curl 脚本
bash assistant-agent-planning/add-actions.sh
```

或 Windows:
```cmd
assistant-agent-planning\add-actions.bat
```

### 3. 测试不同置信度场景

#### 高置信度测试（>= 0.95）
```
用户输入: "添加产品单位"
预期结果: Hook层直接执行，跳过LLM
```

#### 中等置信度测试（0.7-0.95）
```
用户输入: "添加单位"
预期结果: 评估模块注入提示，LLM使用 plan_action 工具
```

#### 低置信度测试（< 0.7）
```
用户输入: "加个东西"
预期结果: 正常ReAct流程
```

### 4. 检查日志

关键日志输出：
```
# Hook 层
UnifiedIntentRecognitionHook#beforeAgent - reason=match found, actionId=..., confidence=...

# Hook 高置信度执行
UnifiedIntentRecognitionHook#handleHighConfidence - reason=found experience/no experience

# 放行到评估模块
UnifiedIntentRecognitionHook#beforeAgent - reason=confidence < 0.95, defer to evaluation module

# 评估层
ActionIntentEvaluator#evaluate - reason=action matched, actionId=..., confidence=...

# 提示构建
ActionIntentPromptBuilder#build - reason=building action intent prompt
```

## 已创建的文件

### 核心实现
1. `UnifiedIntentRecognitionHook.java` - 统一Intent Hook
2. `ActionIntentEvaluator.java` - 动作意图评估器
3. `ActionIntentPromptBuilder.java` - 动作意图提示构建器
4. `PlanningEvaluationCriterionProvider.java` - 评估标准提供者

### 配置
1. `PlanningExtensionAutoConfiguration.java` - 添加了评估集成配置
2. `ActionController.java` - 支持两种Hook（兼容性）
3. `application.yml` - 配置统一模式

### 文档
1. `add-actions.sh` / `add-actions.bat` - 添加动作脚本
2. `add-actions-curl.md` - curl命令文档
3. `UNIFIED_INTENT_INTEGRATION.md` - 本文档

## 配置选项

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `planning.intent.use-unified` | `true` | 是否使用统一Intent Hook |
| `planning.intent.direct-execute-threshold` | `0.95` | 高置信度阈值（Hook直接执行） |
| `planning.intent.hint-threshold` | `0.7` | 中等置信度阈值（评估注入提示） |
| `experience.fast-intent-enabled` | `false` | FastIntent（已禁用，避免冲突） |
| `evaluation.enabled` | `true` | 评估模块（必须启用） |

## 故障排查

### 问题1: Hook未生效

**检查**:
```
grep "UnifiedIntentRecognitionHook" logs/spring.log
```

**解决**:
- 确保 `planning.intent.enabled=true`
- 确保 `planning.intent.use-unified=true`

### 问题2: 评估模块未生效

**检查**:
```
grep "ActionIntentEvaluator" logs/spring.log
grep "PlanningEvaluationCriterionProvider" logs/spring.log
```

**解决**:
- 确保 `evaluation.enabled=true`
- 确保 `EvaluatorRegistry` Bean 已创建

### 问题3: Graph edge 冲突

**错误**:
```
GraphRunnerException: cannot find edge mapping for id: '_AGENT_HOOK_FastIntentReactHook.before'
```

**解决**:
- 确保 `experience.fast-intent-enabled=false`
- 重启应用

## 下一步扩展

1. **添加更多评估标准**: 参数完整性、用户权限等
2. **优化Experience匹配**: 更精确的experience查询策略
3. **添加监控指标**: Hook命中率、执行耗时统计
4. **A/B测试**: 对比Hook直接执行 vs LLM决策的效果
