# 修复：No executor found for step type: INTERNAL_SERVICE

## 🐛 问题原因

**类型不匹配**：
- `InternalExecutor.getExecutorType()` 返回：`"INTERNAL"`
- 数据库配置的 `action_type` 是：`"INTERNAL_SERVICE"` ❌

## ✅ 解决方案

### 快速修复（推荐）

执行修复脚本：

```bash
mysql -u root -p assistant_agent < docs/sql/fix-internal-executor.sql
```

### 或手动更新

```sql
UPDATE action_registry
SET action_type = 'INTERNAL'  -- 改为INTERNAL
WHERE action_id = 'oa:leave:request';
```

---

## 🔍 验证

```sql
SELECT
  action_id,
  action_type,
  handler
FROM action_registry
WHERE action_id = 'oa:leave:request';
```

**预期结果**：
```
action_id          | action_type | handler
------------------|-------------|---------------------------
oa:leave:request | INTERNAL    | oaSystemActionService
```

---

## 📋 类型对应关系

| 数据库 action_type | 执行器 | 说明 |
|-------------------|--------|------|
| `API_CALL` | ApiCallStepExecutor | HTTP API调用 |
| `INTERNAL` | InternalExecutor | Spring Bean方法调用 ✅ |
| `MCP_TOOL` | McpExecutor | MCP工具调用 |
| `MULTI_STEP` | DefaultPlanExecutor | 多步骤流程 |

---

## 🎯 确认InternalExecutor已注册

查看启动日志，应该看到：

```
ActionExecutorFactory#init - initialized executors: HTTP(ApiCallStepExecutor), INTERNAL(InternalExecutor), ...
```

如果没有看到 `INTERNAL(InternalExecutor)`，说明Bean没有注册。检查：

1. `InternalExecutor` 是否有 `@Component` 注解 ✅ (已有)
2. 包扫描是否包含这个类
3. 是否在 `assistant-agent-planning-core` 模块中

---

## 🚀 执行修复后

1. **更新数据库**
   ```sql
   mysql -u root -p assistant_agent < docs/sql/fix-internal-executor.sql
   ```

2. **重启应用**
   ```bash
   mvn spring-boot:run
   ```

3. **测试**
   ```
   用户: 我想明天请假一天
   系统: [识别意图] → [InternalExecutor] → [OaSystemHandler] → OA API ✅
   ```

---

## 📝 总结

**关键点**：
- ✅ `action_type` 必须是 `INTERNAL`（不是 `INTERNAL_SERVICE`）
- ✅ `handler` 是 `oaSystemActionService`（适配Bean）
- ✅ `interface_binding.internal` 配置Bean和方法
- ✅ `OaSystemHandler` 实际执行OA API调用

**执行路径**：
```
Agent → InternalExecutor → oaSystemActionService.execute()
       → OaSystemHandler.execute()
       → OA API with PHPSESSID
```

---

**文档版本**: 1.0.0
**创建时间**: 2026-01-21
**作者**: Assistant Agent Team
