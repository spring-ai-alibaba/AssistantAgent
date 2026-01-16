# Task Plan: 扩展 AssistantAgent 智能规划引擎能力

## Goal
基于 assistant-management 项目的分析结果，扩展 AssistantAgent 的能力，实现企业级智能规划引擎功能，包括：
- 动作匹配与计划生成
- 参数校验与外键依赖处理
- 多步骤执行编排
- 知识库对接与自动化流程

## 背景分析

### AssistantAgent 现有能力
- Code-as-Action 范式（生成代码执行任务）
- GraalVM 沙箱执行
- Evaluation Graph（多维度意图识别）
- Dynamic Prompt Builder
- Experience Learning（经验学习）
- Multi-source Search（多源搜索）
- CodeactTool SPI 扩展

### assistant-management 核心能力（待集成）
- 动作注册与匹配（ActionRegistry, ActionService）
- 参数标准与校验（ActionParameter）
- 外键依赖处理（check_foreign_key）
- 多步骤编排（MultiStepActionService, StepExecutor）
- 状态管理（MultiStepActionState）
- SAGA 事务（补偿机制）
- 接口适配器（HTTP, MCP, Internal）

## Phases

### Phase 1: 架构设计 [COMPLETED]
- ✅ 确定集成方式：新增独立 module (assistant-agent-planning)
- ✅ 设计核心接口和数据模型
- ✅ 规划与现有 CodeactTool 系统的整合

### Phase 2: 动作系统实现 [COMPLETED]
- ✅ ActionDefinition, ActionParameter 数据模型
- ✅ ActionType, StepType, ParameterSource 枚举
- ✅ ActionProvider, ActionRepository SPI 接口
- ✅ InMemoryActionRepository, DefaultActionProvider 实现

### Phase 3: 执行计划生成器 [COMPLETED]
- ✅ ExecutionPlan, ExecutionStep 数据模型
- ✅ PlanGenerator SPI 接口
- ✅ DefaultPlanGenerator 实现
- ✅ 参数来源解析（USER_INPUT, PREVIOUS_STEP, CONTEXT）

### Phase 4: 多步骤执行引擎 [COMPLETED]
- ✅ StepExecutor SPI 接口
- ✅ StepExecutorRegistry 注册表
- ✅ QueryStepExecutor, InputStepExecutor, ExecuteStepExecutor
- ✅ ApiCallStepExecutor, ValidationStepExecutor
- ✅ DefaultPlanExecutor（状态管理、中断恢复）

### Phase 5: CodeactTool 集成 [COMPLETED]
- ✅ BasePlanningCodeactTool 基类
- ✅ PlanActionCodeactTool (plan_action) - 动作匹配和计划生成
- ✅ ExecuteActionCodeactTool (execute_action) - 执行计划
- ✅ ListActionsCodeactTool (list_actions) - 列出可用动作
- ✅ GetActionDetailsCodeactTool (get_action_details) - 获取动作详情
- ✅ PlanningExtensionAutoConfiguration 自动配置

### Phase 6: 测试与验证 [IN PROGRESS]
- ✅ 单元测试 (44 tests passing)
  - ActionDefinitionTest, ActionParameterTest
  - DefaultPlanGeneratorTest, DefaultPlanExecutorTest
  - StepExecutorRegistryTest
- ✅ 示例动作配置
  - leave-apply.yaml (请假申请)
  - query-stock.yaml (库存查询)
  - computer-apply.yaml (电脑申领)
- ⏳ CodeactTools 单元测试
- ⏳ 集成测试

## Progress
- Started: 2026-01-15
- ✅ 已完成 assistant-management 项目分析
- ✅ 已完成 planning 模块核心实现
- ✅ Maven 编译通过
- ✅ 44 个单元测试通过
- ✅ 已添加示例动作配置
- ✅ 修改分词器为 analysis-icu (icu_analyzer)
- ⏳ 待添加 CodeactTools 测试和集成测试
