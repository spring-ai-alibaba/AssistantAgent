# 操作类能力 Provider 协议与实现说明（2026-02-12）

## 1. 背景与目标

本次实现聚焦“操作类型能力”落地，目标是让中台以协议化方式接入各租户外部系统能力，不写死 OA 细节：

1. Provider 由接入方提供 HTTP 服务并按协议对接。
2. 配置按租户隔离（`tenant-providers`）。
3. 当前登录用户先绑定外部账号，再用外部 `userId` 换 token。
4. 支持 token 过期自动 refresh，且 `refresh_token` 加密存储。
5. 槽位补齐阶段支持“先部门后人员”的级联选项、默认审批人、cursor 分页。

## 2. 关键决策落地

### 2.1 注册与路由

- 新增租户级 Provider 配置：`assistant.agent.capability.tenant-providers`。
- 能力定义新增 `provider-code` 和 `submit-action`。
- 能力提交优先走 Provider 协议；若未配置 provider，则回退原有 `endpoint-url` 直连。

### 2.2 身份与鉴权

- 新增绑定模型 `CapabilityUserBinding`（tenant + user + provider + externalUserId + token 信息）。
- 绑定缺失时抛 `BIND_NOT_FOUND`，并在能力工具响应中回传标准错误码。
- token 处理顺序：
  1. access_token 可用直接复用。
  2. 过期且有 refresh_token：走 refresh。
  3. 无 refresh_token：走 exchange。
- refresh_token 使用 `AES/GCM` 加密存储（`TokenCryptoSupport`）。

### 2.3 槽位交互

- `FieldSpec` 新增扩展字段：
  - `input-mode`（`TEXT`/`SELECT_SINGLE`/`SELECT_MULTI`）
  - `option-query-action`
  - `entity-resolve-action`
  - `default-value-action`
  - `depends-on`
  - `option-page-size`
- `SLOT_MISSING` 响应新增 `field_hints`：
  - `options`
  - `next_cursor`
  - `has_more`
  - `default_value`
  - `depends_on`
- 支持按字段 cursor 透传：`{fieldName}_cursor`。

## 3. Provider 协议（中台 -> 接入方）

### 3.1 options_query

路径：`options-query-path`（默认 `/capability/provider/options/query`）

请求体关键字段：
- `action`
- `tool_name`
- `field_name`
- `slots`
- `cursor`
- `limit`
- `tenant_id`
- `user_id`

响应：
```json
{
  "code": "OK",
  "data": {
    "items": [
      { "label": "研发部", "value": "dept-rd", "extra": {} }
    ],
    "cursor": "next-cursor",
    "has_more": true
  }
}
```

### 3.2 default_value

路径：`default-value-path`（默认 `/capability/provider/default/value`）

响应：
```json
{
  "code": "OK",
  "data": {
    "value": "leader-1001",
    "label": "张主管"
  }
}
```

### 3.3 submit

路径：`submit-path`（默认 `/capability/provider/submit`）

请求体关键字段：
- `action`
- `tool_name`
- `form_data`
- `arguments`
- `tenant_id`
- `user_id`

### 3.4 token 交换/刷新

- exchange 路径：`token-exchange-path`
- refresh 路径：`token-refresh-path`
- Header：`token-header-name` + `token-prefix + access_token`

## 4. 标准错误码

- `BIND_NOT_FOUND`
- `PROVIDER_NOT_FOUND`
- `TOKEN_EXCHANGE_FAILED`
- `TOKEN_REFRESH_FAILED`
- `PROVIDER_CALL_FAILED`
- `INVALID_PROVIDER_RESPONSE`
- `INVOCATION_CONTEXT_MISSING`

工具响应中保持 `status=SUBMIT_FAILED`，并补充 `error_code`，确保现有状态机兼容。

## 5. 主要代码改动

- 配置模型增强：
  - `assistant-agent-start/.../CapabilityRegistrationProperties.java`
- Provider 基础能力：
  - `CapabilityProviderConfigRegistry`
  - `CapabilityProviderTokenService`
  - `CapabilityProviderOrchestrator`
  - `DefaultCapabilityProviderClient`
  - `InMemoryCapabilityUserBindingStore`
  - `TokenCryptoSupport`
- 能力工具编排增强：
  - `RegisteredHttpFormCodeactTool`
  - `CapabilityToolRegistrar`
- Hook 启动稳健性：
  - `OperationIntentRouteReactHook` 增加无参构造兜底。

## 6. 当前边界

1. 当前绑定存储为内存实现，生产应替换为 DB 持久化实现。
2. Provider 注册仍以配置加载为主；运行时动态注册接口可后续补充。
3. 本轮仅覆盖“操作类型”能力链路，查询/分析类型后续扩展。
