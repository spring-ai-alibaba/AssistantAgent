# 通用能力编排与配置注册改造总结（2026-02-11）

## 1. 目标与范围

本轮目标是将“工作汇报特化实现”升级为“可复用的通用能力链路”，并打通 YAML 配置驱动注册能力工具的最小可用闭环。

本轮范围包含：
- YAML 配置注册能力工具并在启动时装配。
- 槽位续填与直出逻辑从“工作汇报特化”升级为“通用能力”。
- 保持单实例、启动时加载模型，不做运行时热更新。

## 2. 已完成改造

### 2.1 启动配置驱动能力注册

新增并接入了配置驱动能力注册链路：
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/capability/config/CapabilityRegistrationProperties.java`
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/capability/registry/CapabilityToolRegistrar.java`
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/capability/tool/RegisteredHttpFormCodeactTool.java`

能力配置入口：
- `assistant-agent-start/src/main/resources/application.yml`
- `assistant-agent-start/src/main/resources/application-reference.yml`

实现结果：
- 启动时读取 `assistant.agent.capability.registrations`。
- 生成 `RegisteredHttpFormCodeactTool` 并注入 CodeAct 工具集。
- 同时注入 React 阶段工具集，以支持能力工具在追问/确认阶段直连调用。

### 2.2 通用槽位续填与追问链路

将原工作汇报续填 Hook 泛化为通用能力处理：
- `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/hook/WorkReportDraftResumeReactHook.java`

关键行为：
- 从状态键 `capability_draft_{toolName}_status` 自动识别当前活跃能力。
- 支持 `SLOT_MISSING`、`WAIT_CONFIRM`、`SUBMIT_FAILED` 的续填流程。
- 使用 LLM 进行槽位提取（不依赖正则硬编码业务字段）。
- 在 `WAIT_CONFIRM` 阶段识别确认语义并补 `confirmed=true` 再次调用同一工具。

### 2.3 通用能力结果直出

将模型前清理 Hook 泛化为能力结果直出：
- `assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/hook/FastIntentJumpCleanupModelHook.java`

关键行为：
- 对 `SLOT_MISSING`、`WAIT_CONFIRM`、`SUBMITTED`、`SUBMIT_FAILED` 做统一直出。
- 保留 FastIntent 的 `jump_to=tool` 残留清理逻辑。

### 2.4 提示词与 Demo 清理

更新系统提示词为“通用能力槽位收集规则”：
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/CodeactAgentConfig.java`

移除工作汇报 Demo 快捷经验注入方法与调用：
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/DemoExperienceConfig.java`

对应测试更新：
- `assistant-agent-start/src/test/java/com/alibaba/assistant/agent/start/config/DemoExperienceConfigTest.java`

## 3. 测试与验证

### 3.1 单元测试

能力续填与直出相关测试：
- `assistant-agent-extensions/src/test/java/com/alibaba/assistant/agent/extension/experience/hook/WorkReportDraftResumeReactHookTest.java`
- `assistant-agent-extensions/src/test/java/com/alibaba/assistant/agent/extension/experience/hook/FastIntentJumpCleanupModelHookTest.java`

能力工具序列化测试：
- `assistant-agent-start/src/test/java/com/alibaba/assistant/agent/start/capability/tool/RegisteredHttpFormCodeactToolSerializationTest.java`

Demo 配置测试：
- `assistant-agent-start/src/test/java/com/alibaba/assistant/agent/start/config/DemoExperienceConfigTest.java`

### 3.2 启动验证

验证结果：
- 应用可正常启动并加载能力工具。
- 默认 `8081` 端口被占用时，改用 `18081` 可正常启动。
- 启动日志中可见 `submit_office_work_report` 等能力工具注册信息与服务就绪日志。

## 4. 当前约束

- 当前是“启动时加载 YAML”的注册模式，修改 YAML 后需重启生效。
- 尚未提供“接口发起 + DB 持久化 + 运行时新增加载”的热更新能力。
- 仅验证单实例路径，不涉及多实例同步。

## 5. 下一步建议

按既定路线推进第二阶段：
1. 增加能力注册接口（新增型，append-only）。
2. 落库后触发运行时新增加载（仅新增，不支持修改/删除）。
3. 补齐能力定义版本、幂等键与审计日志。
