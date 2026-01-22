# URI is not absolute - 问题排查与修复指南

## 🐛 问题描述
```
API call failed: URI is not absolute
```

## 🔍 问题原因

`RestTemplate` 要求使用**完整的绝对URL**，不能是相对路径。

### 错误示例 ❌
```
http://office.test + home/leaves/add = http://office.testhome/leaves/add
                              ↑ 缺少斜杠！
```

### 正确示例 ✅
```
http://office.test + /home/leaves/add = http://office.test/home/leaves/add
```

---

## ✅ 解决方案

### 方案1：验证数据库配置（优先）

```sql
-- 检查interface_binding.http.url是否正确
SELECT
  action_id,
  JSON_EXTRACT(interface_binding, '$.http.url') as url,
  JSON_EXTRACT(interface_binding, '$.http.method') as method
FROM action_registry
WHERE action_id = 'oa:leave:request';
```

**预期结果**：
```
action_id           | url                | method
---------------------|--------------------|-------
oa:leave:request    | /home/leaves/add   | POST
```

**如果没有数据或url为空**，执行：
```sql
-- 更新interface_binding
UPDATE action_registry
SET interface_binding = JSON_OBJECT(
  'type', 'HTTP',
  'http', JSON_OBJECT(
    'url', '/home/leaves/add',
    'method', 'POST',
    'headers', JSON_OBJECT('X-Requested-With', 'XMLHttpRequest')
  )
)
WHERE action_id = 'oa:leave:request';
```

---

### 方案2：检查Java代码日志

重新启动应用后，查看日志：

```
OaSystemHandler#execute - calling OA API, actionId=oa:leave:request, url=http://office.test/home/leaves/add
```

**如果url不是完整的绝对URL**，说明配置有问题。

---

### 方案3：使用完整的base URL（推荐）

#### 方案3A：修改硬编码配置

当前 `OaSystemHandler.java` 第184行：
```java
"api_base_url", "http://office.test",
```

**请确认**：
- ✅ 协议：`http://` 或 `https://`
- ✅ 域名：`office.test`
- ✅ 无尾部斜杠

#### 方案3B：使用完整的external_system_config

```sql
-- 确保external_system_config配置正确
UPDATE assistant_agent.external_system_config
SET api_base_url = 'http://office.test'  -- ← 必须是完整URL
WHERE system_id = 'oa-system';
```

---

## 🔧 调试步骤

### 步骤1：验证数据库配置

```sql
-- 1. 检查system_config
SELECT system_id, api_base_url
FROM external_system_config
WHERE system_id = 'oa-system';

-- 2. 检查action配置
SELECT action_id, system_id, handler
FROM action_registry
WHERE action_id = 'oa:leave:request';

-- 3. 检查interface_binding
SELECT
  JSON_EXTRACT(interface_binding, '$.http.url') as endpoint,
  JSON_EXTRACT(interface_binding, '$.http.method') as method
FROM action_registry
WHERE action_id = 'oa:leave:request';
```

### 步骤2：测试URL拼接

手动测试拼接结果是否正确：
```
baseUrl = http://office.test
endpoint = /home/leaves/add

结果 = http://office.test/home/leaves/add ✅
```

### 步骤3：查看应用日志

启动应用，查看日志中的URL：
```
OaSystemHandler#execute - calling OA API, actionId=oa:leave:request, url=...
```

确认url是否为完整的绝对URL。

---

## 🎯 常见错误及修复

| 错误 | 原因 | 修复方法 |
|------|------|----------|
| `http://office.testhome/...` | endpoint缺少前导`/` | 更新SQL：`'url', '/home/leaves/add'` |
| `http://office.test//home/...` | endpoint和baseUrl都有`/` | 使用代码中的URL标准化逻辑 |
| `office.test/home/...` | baseUrl缺少协议 | 更新：`'http://office.test'` |

---

## 📝 验证清单

执行以下SQL验证所有配置：

```sql
-- 完整验证脚本
SELECT
  ar.action_id,
  ar.action_name,
  ar.system_id,
  esc.api_base_url,
  JSON_EXTRACT(ar.interface_binding, '$.type') as binding_type,
  JSON_EXTRACT(ar.interface_binding, '$.http.url') as endpoint,
  JSON_EXTRACT(ar.interface_binding, '$.http.method') as method,
  CONCAT(
    esc.api_base_url,
    CASE
      WHEN JSON_EXTRACT(ar.interface_binding, '$.http.url') LIKE '/%'
      THEN JSON_EXTRACT(ar.interface_binding, '$.http.url')
      ELSE CONCAT('/', JSON_EXTRACT(ar.interface_binding, '$.http.url'))
    END
  ) as full_url
FROM action_registry ar
LEFT JOIN external_system_config esc ON ar.system_id = esc.system_id
WHERE ar.action_id = 'oa:leave:request';
```

**预期输出**：
```
action_id         | full_url
------------------|---------------------------
oa:leave:request  | http://office.test/home/leaves/add
```

---

## ✅ 修复后的效果

正确配置后，Agent应该能够：

```
用户: 我想明天请假一天
Agent: [调用execute_system_action]
      → OaSystemHandler.execute()
      → GET PHPSESSID: http://office.test/api/oa_integration/get_phpsessid
      → POST 请假: http://office.test/home/leaves/add
      → 返回: {"success": true, "message": "申请已提交"}
```

---

如果问题仍未解决，请提供：
1. 上述SQL查询的结果
2. 应用日志中的URL
3. 完整的错误堆栈

---

**文档版本**: 1.0.0
**创建时间**: 2026-01-21
