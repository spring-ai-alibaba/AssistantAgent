# OA请假集成 - OaSystemHandler改造完成

## ✅ 改造内容

### 问题根源
从日志看到，系统使用的是 `ApiCallStepExecutor` 而不是 `OaSystemHandler`：
```
ApiCallStepExecutor : calling API, url=/home/leaves/add
```

### 解决方案
将Action类型改为 `INTERNAL_SERVICE`，通过Spring Bean调用 `OaSystemHandler`。

---

## 🏗️ 新架构

### 执行流程
```
用户输入："我想明天请假一天"
  ↓
UnifiedIntentRecognitionHook（意图识别）
  ↓
DefaultPlanGenerator（生成计划）
  ↓
DefaultPlanExecutor（执行计划）
  ↓
InternalExecutor（因为是INTERNAL_SERVICE类型）✅
  ↓
oaSystemActionService.execute(params) [Spring Bean]
  ↓ 提取actionId和params
OaSystemHandler.execute(actionId, params, context)
  ↓
OA API调用
```

---

## 📦 新增文件

### 1. OaSystemActionService.java
**路径**: `assistant-agent-planning-core/.../system/OaSystemActionService.java`

**作用**：
- 适配 `InternalExecutor` 的调用方式
- 提供简化的方法签名：`execute(Map<String, Object> params)`
- 内部委托给 `OaSystemHandler`

**关键代码**：
```java
@Component("oaSystemActionService")
public class OaSystemActionService {

    private final OaSystemHandler oaSystemHandler;

    public Map<String, Object> execute(Map<String, Object> params) {
        String actionId = (String) params.getOrDefault("action_id", "oa:leave:request");
        Map<String, Object> actionParams = extractActionParams(params);
        Map<String, Object> context = (Map<String, Object>) params.get("context");

        return oaSystemHandler.execute(actionId, actionParams, context);
    }
}
```

---

## 🗄️ 数据库配置

### SQL脚本
**文件**: `docs/sql/oa-leave-use-oashandler.sql`

### 关键配置
```sql
action_type = 'INTERNAL_SERVICE'
handler = 'oaSystemActionService'

interface_binding = {
  "type": "INTERNAL",
  "internal": {
    "beanName": "oaSystemActionService",
    "methodName": "execute",
    "methodParams": [{
      "name": "params",
      "type": "java.util.Map"
    }]
  }
}
```

---

## 🚀 部署步骤

### 步骤1：编译Java代码
```bash
cd D:/devfive/AssistantAgent
mvn clean compile
```

### 步骤2：执行SQL
```bash
mysql -u root -p assistant_agent < docs/sql/oa-leave-use-oashandler.sql
```

### 步骤3：重启应用
```bash
cd assistant-agent-start
mvn spring-boot:run
```

### 步骤4：验证配置
```sql
SELECT
  action_id,
  action_type,
  handler,
  JSON_EXTRACT(interface_binding, '$.internal.beanName') as bean,
  JSON_EXTRACT(interface_binding, '$.internal.methodName') as method
FROM action_registry
WHERE action_id = 'oa:leave:request';
```

**预期输出**：
```
action_id           | action_type       | bean                    | method
---------------------|--------------------|-------------------------|--------
oa:leave:request    | INTERNAL_SERVICE   | oaSystemActionService  | execute
```

---

## 🧪 测试

### 测试场景
```
用户: 我想明天请假一天
系统: [识别意图]
     [参数收集: start_date, end_date, types, reason, check_uids]
     [调用: oaSystemActionService → OaSystemHandler]
     [返回: {"success": true, "message": "申请已提交"}]
```

### 查看日志
应该看到：
```
OaSystemHandler#execute - actionId=oa:leave:request, systemId=oa-system
OaSystemHandler#execute - calling OA API, actionId=oa:leave:request, url=http://office.test/home/leaves/add
OaSystemHandler#execute - completed, actionId=oa:leave:request, time=XXXms
```

---

## 📋 优势对比

| 对比项 | 之前（API_CALL） | 现在（INTERNAL_SERVICE） |
|--------|-------------------|-------------------------|
| 执行器 | ApiCallStepExecutor | InternalExecutor ✅ |
| URL处理 | 相对路径❌ | OaSystemHandler处理✅ |
| Session管理 | 无 | 自动获取PHPSESSID✅ |
| 系统隔离 | 困难 | 通过system_id隔离✅ |
| 扩展性 | 低 | 添加新Action只需配置✅ |

---

## ❗ 注意事项

### 1. action_id的传递
由于 `OaSystemActionService.execute()` 只接收一个 `params` Map，`action_id` 需要通过以下方式之一传递：

**方式A**：在SQL中设置默认值（推荐）
```sql
-- 在OaSystemActionService中
String actionId = (String) params.getOrDefault("action_id", "oa:leave:request");
```

**方式B**：通过context传递
```java
Map<String, Object> context = new HashMap<>();
context.put("action_id", "oa:leave:request");
```

### 2. 参数清理
`OaSystemActionService` 会自动清理系统参数（action_id, context），只传递业务参数给 `OaSystemHandler`。

### 3. context传递
当前context是从params中提取的。如果需要传递userId等上下文信息：
```java
// 在UnifiedIntentRecognitionHook中构建params时添加
params.put("context", Map.of("userId", getCurrentUserId()));
```

---

## 🎯 总结

✅ **已实现**：
- 创建 `OaSystemActionService` 适配类
- 修改SQL使用 `INTERNAL_SERVICE` 类型
- 通过Spring Bean调用 `OaSystemHandler`

✅ **优势**：
- 统一使用 `OaSystemHandler` 处理所有OA操作
- 支持session管理、URL拼接等复杂逻辑
- 易于扩展：添加新OA操作只需配置

✅ **下一步**：
- 执行SQL脚本
- 重启应用测试
- 验证端到端流程

---

**文档版本**: 2.0.0
**创建时间**: 2026-01-21
**作者**: Assistant Agent Team
