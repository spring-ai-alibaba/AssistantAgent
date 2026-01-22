# OA系统集成部署指南

## 📋 目录

1. [环境要求](#环境要求)
2. [快速开始](#快速开始)
3. [详细步骤](#详细步骤)
4. [配置说明](#配置说明)
5. [测试验证](#测试验证)
6. [常见问题](#常见问题)

---

## 环境要求

### AssistantAgent环境
- Java 17+
- Maven 3.6+
- MySQL 5.7+ 或 8.0+
- Redis 6.0+ (可选，用于Token缓存)
- Spring Boot 3.4.8

### OA系统环境
- PHP 8.0+
- MySQL 5.7+
- Apache/Nginx
- ThinkPHP 8.0

---

## 快速开始

### 5分钟快速部署

```bash
# 1. 初始化AssistantAgent数据库
mysql -u root -p assistant_agent < docs/sql/oa-integration-mysql.sql

# 2. 初始化OA绑定表
mysql -u root -p oa_database < docs/sql/oa-integration-bind-table.sql

# 3. 添加用户绑定
mysql -u root -p assistant_agent -e "
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, bind_type)
VALUES ('M001', 'U001', 'oa-system', '1', 'MANUAL');"

# 4. 配置application.yml
# 修改 spring.ai.alibaba.oa.api-base-url

# 5. 启动系统
# - 启动OA系统
# - 启动AssistantAgent

# 6. 测试
curl -X POST http://localhost:8081/api/oa_integration/get_token \
  -H "Content-Type: application/json" \
  -d '{"assistant_user_id":"U001"}'
```

---

## 详细步骤

### 步骤1: 准备MySQL数据库

#### 1.1 创建AssistantAgent数据库

```sql
CREATE DATABASE IF NOT EXISTS assistant_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE assistant_agent;
```

#### 1.2 导入数据库表结构

```bash
# Windows
cd D:\devfive\AssistantAgent\docs
mysql -u root -p assistant_agent < sql\oa-integration-mysql.sql

# Linux/Mac
cd /path/to/AssistantAgent/docs
mysql -u root -p assistant_agent < sql/oa-integration-mysql.sql
```

#### 1.3 导入初始数据

```bash
mysql -u root -p assistant_agent < sql/oa-integration-init-data.sql
```

#### 1.4 验证表结构

```sql
-- 查看已创建的表
USE assistant_agent;
SHOW TABLES;

-- 应该看到：
-- platform_user
-- platform_user_role
-- external_system_config
-- user_identity_mapping
-- action_permission_config
-- permission_audit_log
```

### 步骤2: 配置AssistantAgent

#### 2.1 修改application.yml

编辑文件: `assistant-agent-start/src/main/resources/application.yml`

```yaml
spring:
  # 数据源配置
  datasource:
    url: jdbc:mysql://localhost:3306/assistant_agent?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  # Redis配置（可选）
  data:
    redis:
      host: localhost
      port: 6379
      database: 0

# MyBatis Plus配置
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

# OA集成配置
spring.ai.alibaba:
  oa:
    enabled: true
    api-base-url: http://localhost:8081
    timeout: 10000

  permission:
    enabled: true
    persistence-type: mysql
    auto-init-schema: false

# 日志配置
logging:
  level:
    com.alibaba.assistant.agent: DEBUG
```

#### 2.2 构建项目

```bash
cd D:/devfive/AssistantAgent
mvn clean install -DskipTests
```

#### 2.3 启动AssistantAgent

```bash
cd assistant-agent-start
mvn spring-boot:run
```

### 步骤3: 配置OA系统

#### 3.1 创建绑定表

```bash
# 进入OA安装目录
cd D:/phpstudy_pro/WWW/office/app/install/data

# 导入绑定表
mysql -u root -p oa_database < oa_assistant_bind.sql
```

#### 3.2 添加绑定关系

方式一：通过SQL直接插入

```sql
USE oa_database;

INSERT INTO oa_assistant_agent_bind
(assistant_user_id, oa_user_id, bind_time, status, create_time, update_time)
VALUES
('U001', 1, UNIX_TIMESTAMP(), 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());

-- 验证绑定
SELECT * FROM oa_assistant_agent_bind WHERE assistant_user_id = 'U001';
```

方式二：通过OA管理界面（如果开发）

#### 3.3 验证OA API

```bash
# 测试接口是否可访问
curl http://localhost:8081/api/oa_integration/test

# 应该返回：
# {"code":0,"msg":"OA集成API正常工作"}
```

### 步骤4: 双向绑定数据

为了确保数据一致，需要在两个系统中都添加绑定关系：

#### AssistantAgent侧

```sql
USE assistant_agent;

INSERT INTO user_identity_mapping
(id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type)
VALUES
('M001', 'U001', 'oa-system', '1', 'admin',
 '{"role": "admin", "deptId": "1", "deptName": "总公司"}',
 'MANUAL');
```

#### OA系统侧

```sql
USE oa_database;

INSERT INTO oa_assistant_agent_bind
(assistant_user_id, oa_user_id, bind_time, status, create_time, update_time)
VALUES
('U001', 1, UNIX_TIMESTAMP(), 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());
```

---

## 配置说明

### 数据库连接配置

| 参数 | 说明 | 示例 |
|------|------|------|
| spring.datasource.url | JDBC连接URL | jdbc:mysql://localhost:3306/assistant_agent |
| spring.datasource.username | 数据库用户名 | root |
| spring.datasource.password | 数据库密码 | your_password |

### OA系统集成配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| spring.ai.alibaba.oa.enabled | 是否启用OA集成 | true |
| spring.ai.alibaba.oa.api-base-url | OA系统API地址 | http://localhost:8081 |
| spring.ai.alibaba.oa.timeout | 超时时间（毫秒） | 10000 |

### Redis缓存配置（可选）

| 参数 | 说明 | 默认值 |
|------|------|--------|
| spring.cache.type | 缓存类型 | redis |
| spring.cache.redis.time-to-live | Token缓存时间（毫秒） | 7200000 (2小时) |

**注意**：如果没有Redis，可以设置 `spring.cache.type: none`，系统会禁用缓存。

---

## 测试验证

### 测试1: OA API可用性

```bash
# 测试OA集成API
curl http://localhost:8081/api/oa_integration/test
```

**预期结果**:
```json
{
  "code": 0,
  "msg": "OA集成API正常工作"
}
```

### 测试2: 获取Token

```bash
curl -X POST http://localhost:8081/api/oa_integration/get_token \
  -H "Content-Type: application/json" \
  -d "{\"assistant_user_id\":\"U001\"}"
```

**预期结果**:
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

### 测试3: AssistantAgent集成测试

```bash
cd D:/devfive/AssistantAgent

# 运行集成测试
mvn test -Dtest=OaIntegrationTest
```

### 测试4: 端到端测试

1. 启动AssistantAgent
2. 打开Agent对话界面
3. 输入: "查询U001用户在OA系统中的信息"
4. 验证是否能正确返回用户信息

---

## 常见问题

### Q1: Token获取失败，提示"未找到绑定用户"

**原因**: 用户未绑定

**解决方法**:
```sql
-- 检查AssistantAgent侧
SELECT * FROM assistant_agent.user_identity_mapping
WHERE platform_user_id = 'U001' AND system_id = 'oa-system';

-- 检查OA侧
SELECT * FROM oa_database.oa_assistant_agent_bind
WHERE assistant_user_id = 'U001';

-- 如果不存在，插入绑定关系（参考上面的步骤4）
```

### Q2: API调用超时

**原因**:
1. OA系统未启动
2. 端口不通
3. 防火墙阻止

**解决方法**:
```bash
# 检查OA系统
curl http://localhost:8081

# 检查端口
netstat -ano | findstr "8081"

# 检查防火墙（Windows）
# 或
sudo ufw status  # Linux
```

### Q3: 数据库连接失败

**原因**: 数据库未启动或配置错误

**解决方法**:
```bash
# 检查MySQL
mysql -u root -p

# 检查配置
# 确认 application.yml 中的数据库配置正确

# 检查数据库是否存在
SHOW DATABASES;
```

### Q4: Redis连接失败（可选）

**原因**: Redis未启动

**解决方法**:
```bash
# 检查Redis
redis-cli ping

# 如果没有Redis，禁用缓存
spring.cache.type: none
```

### Q5: 权限查询失败

**原因**:
1. OA用户不存在
2. 用户被禁用
3. Token过期

**解决方法**:
```sql
-- 检查OA用户状态
SELECT id, username, name, status
FROM oa_admin
WHERE id = 1;

-- Token有效期2小时，过期需要重新获取
```

---

## 部署检查清单

### 部署前检查

- [ ] MySQL已安装并启动
- [ ] Redis已安装并启动（可选）
- [ ] OA系统已安装并可访问
- [ ] Java 17+已安装
- [ ] Maven已安装

### 数据库检查

- [ ] assistant_agent数据库已创建
- [ ] 所有表已创建（6张表）
- [ ] 初始数据已导入
- [ ] OA绑定表已创建
- [ ] 绑定关系已添加

### 配置检查

- [ ] application.yml数据库配置正确
- [ ] OA API地址配置正确
- [ ] 日志级别配置正确

### 功能检查

- [ ] OA API可访问
- [ ] 可以获取Token
- [ ] 可以查询用户信息
- [ ] 可以查询权限信息
- [ ] Agent可以调用工具

---

## 生产环境建议

### 安全加固

1. **使用HTTPS**
   ```yaml
   spring.ai.alibaba.oa.api-base-url: https://oa.yourdomain.com
   ```

2. **修改数据库密码**
   - 使用强密码
   - 限制数据库访问IP

3. **IP白名单**
   - 在OA系统中添加IP白名单
   - 限制API调用来源

4. **Token加密**
   - 生产环境建议对Token进行额外加密

### 性能优化

1. **启用Redis缓存**（必须）
   ```yaml
   spring.cache.type: redis
   ```

2. **数据库连接池**
   ```yaml
   spring.datasource.hikari:
     maximum-pool-size: 20
     minimum-idle: 5
   ```

3. **日志级别**
   ```yaml
   logging.level.com.alibaba.assistant.agent: INFO
   ```

### 监控告警

1. **监控指标**
   - Token获取耗时
   - API调用成功率
   - 数据库连接池使用率
   - Redis缓存命中率

2. **日志收集**
   - 使用ELK或类似工具收集日志
   - 设置关键错误告警

---

## 备份与恢复

### 数据库备份

```bash
#!/bin/bash
# backup.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR=/backup/assistant_agent

# 备份AssistantAgent数据库
mysqldump -u root -p assistant_agent > $BACKUP_DIR/assistant_agent_$DATE.sql

# 备份OA数据库
mysqldump -u root -p oa_database > $BACKUP_DIR/oa_database_$DATE.sql

# 删除7天前的备份
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete

echo "Backup completed: $DATE"
```

### 数据库恢复

```bash
# 恢复AssistantAgent数据库
mysql -u root -p assistant_agent < backup/assistant_agent_20250121.sql

# 恢复OA数据库
mysql -u root -p oa_database < backup/oa_database_20250121.sql
```

---

## 升级指南

### 版本升级步骤

1. **备份数据**
   ```bash
   mysqldump -u root -p assistant_agent > backup.sql
   ```

2. **停止服务**
   ```bash
   # 停止AssistantAgent
   # 停止OA系统
   ```

3. **更新代码**
   ```bash
   cd D:/devfive/AssistantAgent
   git pull
   mvn clean install
   ```

4. **执行数据库迁移**（如果有）
   ```bash
   mysql -u root -p assistant_agent < migration.sql
   ```

5. **启动服务**
   ```bash
   # 启动OA系统
   # 启动AssistantAgent
   ```

6. **验证升级**
   ```bash
   mvn test
   ```

---

## 支持与联系

- **文档**: [https://github.com/alibaba/assistant-agent](https://github.com/alibaba/assistant-agent)
- **Issue**: [GitHub Issues](https://github.com/alibaba/assistant-agent/issues)

---

**文档版本**: 1.0.0
**最后更新**: 2025-01-21
**作者**: Assistant Agent Team
