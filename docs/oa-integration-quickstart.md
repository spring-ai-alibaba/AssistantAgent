# OA系统集成快速开始指南

## 📖 概述

本文档提供AssistantAgent与勾股OA系统集成的快速开始指南。

### 已完成的文件

#### OA系统侧
- ✅ `app/api/controller/OaIntegration.php` - API控制器
- ✅ `app/install/data/oa_assistant_bind.sql` - 绑定表SQL
- ✅ `route/app.php` - 路由配置（已更新）

#### AssistantAgent侧
- ✅ `docs/oa-integration-design.md` - 设计文档
- ✅ `docs/oa-integration-api.md` - API接口文档
- ✅ `docs/oa-integration-deployment.md` - 详细部署指南
- ✅ `docs/sql/oa-integration-mysql.sql` - 数据库表结构
- ✅ `docs/sql/oa-integration-init-data.sql` - 初始数据

---

## 🚀 快速开始（5分钟部署）

### 步骤1: 初始化OA系统绑定表

```bash
# 进入OA目录
cd D:/phpstudy_pro/WWW/office/app/install/data

# 导入绑定表
mysql -u root -p oa_database < oa_assistant_bind.sql

# 注意：将 oa_database 替换为您的OA数据库名
```

**验证**：
```sql
USE oa_database;
SELECT * FROM oa_assistant_agent_bind;
```

应该看到3条示例数据。

### 步骤2: 初始化AssistantAgent数据库

```bash
# 进入AssistantAgent文档目录
cd D:/devfive/AssistantAgent/docs/sql

# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS assistant_agent DEFAULT CHARACTER SET utf8mb4;"

# 导入表结构
mysql -u root -p assistant_agent < oa-integration-mysql.sql

# 导入初始数据
mysql -u root -p assistant_agent < oa-integration-init-data.sql
```

**验证**：
```sql
USE assistant_agent;
SHOW TABLES;
-- 应该看到6张表

SELECT * FROM external_system_config;
-- 应该看到OA系统配置

SELECT * FROM user_identity_mapping;
-- 应该看到3条绑定数据
```

### 步骤3: 配置用户绑定

**重要**：确保两边的数据一致！

#### 方法1: 通过SQL添加（快速）

```sql
-- 在OA数据库中添加（如果还没有）
USE oa_database;
INSERT INTO oa_assistant_agent_bind
(assistant_user_id, oa_user_id, bind_time, status, create_time, update_time)
VALUES
('U001', 1, UNIX_TIMESTAMP(), 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());

-- 在AssistantAgent数据库中已经有（init-data.sql中已添加）
USE assistant_agent;
-- user_identity_mapping 表中已有 U001 的绑定数据
```

#### 方法2: 查询验证

```sql
-- OA侧
SELECT b.*, a.username, a.name
FROM oa_database.oa_assistant_agent_bind b
LEFT JOIN oa_database.oa_admin a ON b.oa_user_id = a.id
WHERE b.assistant_user_id = 'U001';

-- AssistantAgent侧
SELECT m.*, p.name AS platform_user_name
FROM assistant_agent.user_identity_mapping m
LEFT JOIN assistant_agent.platform_user p ON m.platform_user_id = p.id
WHERE m.platform_user_id = 'U001' AND m.system_id = 'oa-system';
```

### 步骤4: 测试OA API

```bash
# 测试API是否可用
curl http://localhost:8081/api/oa_integration/test

# 应该返回：
# {"code":0,"msg":"OA集成API正常工作","data":{...}}
```

### 步骤5: 测试获取Token

```bash
curl -X POST http://localhost:8081/api/oa_integration/get_token \
  -H "Content-Type: application/json" \
  -d "{\"assistant_user_id\":\"U001\"}"
```

**预期返回**：
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
    "oa_user_id": "1",
    "username": "admin",
    "name": "管理员"
  }
}
```

---

## 📝 配置说明

### OA系统侧配置

#### 修改OA用户ID映射

如果您的OA系统中用户ID不是1、2、3，需要修改绑定数据：

```sql
-- 1. 查询OA系统中的用户ID
SELECT id, username, name FROM oa_admin ORDER BY id LIMIT 10;

-- 2. 根据实际ID更新绑定表
UPDATE oa_assistant_agent_bind SET oa_user_id = <实际的OA用户ID> WHERE assistant_user_id = 'U001';

-- 3. 同时更新AssistantAgent侧
UPDATE assistant_agent.user_identity_mapping
SET external_user_id = '<实际的OA用户ID>'
WHERE platform_user_id = 'U001' AND system_id = 'oa-system';
```

### AssistantAgent侧配置

#### 修改OA API地址

编辑 `assistant-agent-start/src/main/resources/application.yml`:

```yaml
spring.ai.alibaba.oa:
  enabled: true
  api-base-url: http://localhost:8081  # 修改为实际OA地址
```

如果OA系统不在本地，请修改为实际地址：
```yaml
api-base-url: http://192.168.1.100:8081
# 或
api-base-url: http://oa.yourdomain.com
```

---

## 🔍 故障排查

### 问题1: API返回404

**原因**：路由未生效或URL不正确

**解决**：
```bash
# 1. 确认路由文件已更新
cat route/app.php | grep oa_integration

# 2. 清除ThinkPHP缓存
rm -rf runtime/cache/*

# 3. 检查URL是否正确
# 应该是: http://localhost:8081/api/oa_integration/test
```

### 问题2: Token获取失败 - "未找到绑定用户"

**原因**：绑定关系不存在或状态被禁用

**解决**：
```sql
-- 检查OA侧绑定
SELECT * FROM oa_assistant_agent_bind WHERE assistant_user_id = 'U001';

-- 检查状态是否为1
UPDATE oa_assistant_agent_bind SET status = 1 WHERE assistant_user_id = 'U001';

-- 检查OA用户是否存在
SELECT * FROM oa_admin WHERE id = (SELECT oa_user_id FROM oa_assistant_agent_bind WHERE assistant_user_id = 'U001');
```

### 问题3: 数据库连接失败

**原因**：数据库未启动或配置错误

**解决**：
```bash
# 检查MySQL
mysql -u root -p

# 检查数据库是否存在
SHOW DATABASES;

# 检查表是否存在
USE assistant_agent;
SHOW TABLES;
```

---

## 📊 验证清单

部署完成后，请逐项检查：

- [ ] OA绑定表已创建并导入数据
- [ ] AssistantAgent数据库表已创建
- [ ] 初始数据已导入
- [ ] API路由已配置
- [ ] OaIntegration.php控制器已放置
- [ ] 绑定关系已添加（两边都有）
- [ ] OA API可以访问（test接口返回正常）
- [ ] 可以成功获取Token
- [ ] Token可以查询用户信息

---

## 🎯 下一步

完成基础部署后，您可以：

1. **测试Agent工具调用**
   - 启动AssistantAgent
   - 尝试调用 `oa_integration` 工具

2. **开发更多功能**
   - 创建CodeactTool工具类
   - 实现权限适配器
   - 添加更多业务逻辑

3. **生产环境部署**
   - 配置HTTPS
   - 启用Redis缓存
   - 配置IP白名单
   - 设置监控告警

---

## 📚 详细文档

- [完整设计方案](./oa-integration-design.md)
- [API接口文档](./oa-integration-api.md)
- [详细部署指南](./oa-integration-deployment.md)

---

## 🆘 获取帮助

遇到问题？

1. 查看日志文件
   - OA: `runtime/log/error.log`
   - AssistantAgent: `logs/assistant-agent.log`

2. 检查数据库
   - 确认表和数据都已正确创建

3. 验证网络
   - 确认OA系统可访问
   - 确认端口8081开放

---

**文档版本**: 1.0.0
**创建时间**: 2025-01-21
**作者**: Assistant Agent Team
