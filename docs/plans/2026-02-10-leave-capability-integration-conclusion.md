# 请假能力动态接入结论（2026-02-10）

## 1. 目标确认
目标是让用户通过自然语言发起请假操作，能力可由接入方动态注册和扩展，接入方提供业务接口与能力注册信息。

## 2. 当前代码基线结论
仓库已具备“动态工具接入”能力，主要结论如下：

- 已有 HTTP/OpenAPI 动态工具链：  
  `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/dynamic/http/HttpDynamicToolFactory.java`
- 已有 MCP 动态工具链：  
  `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/dynamic/mcp/McpDynamicToolFactory.java`
- 启动模块已预留动态装配入口：  
  `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/CodeactAgentConfig.java`  
  其中 `createMcpDynamicTools()` 已可用，`createHttpDynamicTools()` 当前默认关闭（返回空列表）。

结论：**请假能力可以先按“启动时动态注册”快速落地**，不需要重构核心执行链路。

## 3. 接入方需要提供的最小契约
建议至少提供以下能力接口（OpenAPI 或 MCP 均可）：

- `submitLeave`：发起请假
- `getLeaveBalance`：查询余额
- `getLeaveStatus`：查询单据状态
- `cancelLeave`：撤销申请

同时提供：

- 鉴权方式（Header/Token）
- 租户与用户映射字段
- 幂等字段（如 requestId）
- 错误码规范（参数错误、余额不足、审批流失败、权限不足）

## 4. 推荐落地路径
### 阶段A（优先）：启动时注册
- 在 `CodeactAgentConfig#createHttpDynamicTools()` 中读取接入方 OpenAPI 和 endpoint 白名单。
- 生成 `CodeactTool` 并合并到 `.codeactTools(allCodeactTools)`。
- 先跑通“请假申请/查询”核心闭环。

### 阶段B：运行时热注册（无重启）
若要求“在线新增/下线能力”，需要新增改造：

- 扩展 `CodeactToolRegistry`（目前只有 `register/get/list`，缺少 `unregister/replace/version`）。
- 让代码生成阶段的工具源支持动态刷新（当前 `CodeGeneratorNode` 持有构造时注入的 `List<CodeactTool>`）。
- 增加能力管理入口（注册、下线、灰度、按租户/角色可见）。

## 5. 风险与治理建议
- 请假属于写操作，必须做确认策略：缺关键字段先追问，提交前二次确认。
- 当前系统提示词中“不要反问用户”不适合写操作场景，需按工具类型区分策略。
- 必须加权限校验、审计日志、幂等控制与失败补偿（重试/回滚提示）。

## 6. 最终建议
先按阶段A交付可用版本，再进入阶段B做热注册能力。这样可以最短路径上线，同时保留后续扩展空间。
