# 修复：InternalExecutor 参数传递问题

## 🐛 问题原因

**InternalExecutor.prepareArguments()** 方法按照 Map.entrySet() 顺序赋值参数，导致参数传递错误。

### 原实现问题

```java
// 按参数名顺序赋值
int index = 0;
for (Map.Entry<String, Object> entry : params.entrySet()) {
    if (index < args.length) {
        args[index] = convertType(entry.getValue(), paramTypes[index]);
        index++;
    }
}
```

**问题分析**：
1. Map.entrySet() 的顺序是不确定的
2. SQL 中配置的 `methodParams` 是 `[{name: "params", type: "java.util.Map"}]`
3. `oaSystemActionService.execute(Map<String, Object> params)` 期望接收整个 params Map
4. 原实现会将 params 中的每个 entry 作为单独参数传递

### 示例

**SQL 配置**：
```json
{
  "beanName": "oaSystemActionService",
  "methodName": "execute",
  "methodParams": [{
    "name": "params",
    "type": "java.util.Map"
  }]
}
```

**传入的 params**：
```json
{
  "action_id": "oa:leave:request",
  "start_date": "2026-01-23 09:00",
  "context": {...}
}
```

**原实现行为（错误）**：
- 将 params.entrySet() 遍历
- 可能将 "action_id" 作为第一个参数，"start_date" 作为第二个参数
- 导致类型转换错误或参数数量不匹配

**正确行为**：
- 方法签名：`execute(Map<String, Object> params)`
- 应该将整个 params Map 作为单个参数传递

---

## ✅ 解决方案

### 修复后实现

```java
private Object[] prepareArguments(Method method, Map<String, Object> params) {
    Class<?>[] paramTypes = method.getParameterTypes();
    Object[] args = new Object[paramTypes.length];

    if (params == null || params.isEmpty()) {
        return args;
    }

    // 当前实现：如果方法只有一个 Map 类型参数，直接传入整个 params
    if (paramTypes.length == 1 && Map.class.isAssignableFrom(paramTypes[0])) {
        args[0] = params;
        logger.debug("InternalExecutor#prepareArguments - passing entire params as Map argument");
        return args;
    }

    // 后备方案：多参数方法按照 entry 顺序
    // ...
}
```

### 关键改进

1. **检测方法签名**：判断方法是否只有一个 Map 类型参数
2. **直接传递 Map**：如果是，将整个 params Map 作为参数传递
3. **保留后备逻辑**：对于其他情况，使用原有逻辑

---

## 🚀 部署步骤

1. **编译项目**
   ```bash
   cd D:/devfive/AssistantAgent
   mvn clean compile
   ```

2. **重启应用**
   ```bash
   cd assistant-agent-start
   mvn spring-boot:run
   ```

3. **测试**
   - 访问 ChatUI: `http://localhost:8080/chatui/index.html`
   - 输入: "我想明天请假一天"

---

## 📋 预期日志

修复后，应该能看到以下日志：

```
InternalExecutor#execute - invoking bean method, actionId=oa:leave:request, bean=oaSystemActionService, method=execute
InternalExecutor#prepareArguments - passing entire params as Map argument
InternalExecutor#execute - method invoked successfully, actionId=oa:leave:request, time=XXms
OaSystemActionService#execute - actionId=oa:leave:request
OaSystemHandler#execute - actionId=oa:leave:request, systemId=oa-system
OaSystemHandler#execute - calling OA API, actionId=oa:leave:request, url=http://office.test/home/leaves/add
```

---

## 🔍 调试

如果仍然没有进入 OaSystemHandler，检查：

### 1. 确认 Action 配置

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

**预期结果**：
- action_type = `INTERNAL`
- handler = `oaSystemActionService`
- bean = `oaSystemActionService`
- method = `execute`

### 2. 确认 Spring Bean 注册

查看启动日志，应该看到：
```
OaSystemHandler#init - initialized
InternalExecutor#init - initialized
```

### 3. 确认执行路径

查看日志中的执行链路：
- `UnifiedIntentRecognitionHook` - 意图识别
- `DefaultPlanGenerator` - 生成计划
- `DefaultPlanExecutor` - 执行计划
- `InternalExecutor` - 调用 Bean 方法
- `OaSystemActionService` - 适配服务
- `OaSystemHandler` - 执行 OA API

---

**文档版本**: 1.0.0
**创建时间**: 2026-01-22
**作者**: Assistant Agent Team
