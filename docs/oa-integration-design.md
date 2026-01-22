# OA系统集成完整设计方案

## 📋 项目概述

本项目实现**AssistantAgent**与**勾股OA办公系统**的完整集成。

**集成目标**：
- 用户身份绑定（AssistantAgent用户 ↔ OA用户）
- 统一登录认证（基于JWT Token）
- 用户信息查询
- 权限信息同步（角色权限 + 数据权限）
- Agent工具调用支持

**技术栈**：
- AssistantAgent: Java 17 + Spring Boot 3.4.8 + MyBatis Plus + MySQL + Redis
- OA System: PHP 8.0 + ThinkPHP 8 + MySQL

---

## 🏗️ 系统架构

```
┌─────────────────────────┐         ┌─────────────────────────┐
│  AssistantAgent         │         │  OA System              │
│  (Java/Spring Boot)     │         │  (PHP/ThinkPHP)         │
├─────────────────────────┤         ├─────────────────────────┤
│                        │         │                        │
│  - MysqlIdentityMapping│────────│  - oa_assistant_agent_ │
│    Service              │  HTTP   │    bind table          │
│  - OaIntegrationClient  │  API    │                        │
│  - OaTokenCacheManager  │  Token  │  - OaIntegration API   │
│  - OaPermissionAdapter │         │    Controller          │
│  - CodeactTool          │         │                        │
│                        │         │  - Admin/User/Dept     │
│  MySQL + Redis          │         │    Tables              │
│                        │         │                        │
└─────────────────────────┘         └─────────────────────────┘
```

---

## 📂 目录结构

### OA系统侧（PHP）

```
D:/phpstudy_pro/WWW/office/
├── app/api/controller/
│   └── OaIntegration.php          # OA集成API控制器
├── app/api/route/
│   └── app.php                     # API路由配置
├── app/install/data/
│   └── oa_assistant_bind.sql      # 绑定表SQL
└── docs/
    └── oa-integration-api.md      # API文档
```

### AssistantAgent侧（Java）

```
assistant-agent-planning/
├── assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/
│   ├── persistence/
│   │   ├── entity/
│   │   │   ├── UserIdentityMappingEntity.java
│   │   │   └── ExternalSystemConfigEntity.java
│   │   ├── mapper/
│   │   │   ├── UserIdentityMappingMapper.java
│   │   │   └── ExternalSystemConfigMapper.java
│   │   ├── converter/
│   │   │   ├── UserIdentityMappingConverter.java
│   │   │   └── ExternalSystemConfigConverter.java
│   │   └── MysqlIdentityMappingService.java
│   ├── client/oa/
│   │   ├── OaIntegrationClient.java
│   │   ├── OaTokenCacheManager.java
│   │   └── dto/
│   │       ├── OaTokenRequest.java
│   │       ├── OaTokenResponse.java
│   │       ├── OaUserInfoResponse.java
│   │       └── OaPermissionsResponse.java
│   ├── permission/adapter/
│   │   └── OaPermissionAdapterEnhanced.java
│   └── config/
│       ├── OaIntegrationAutoConfiguration.java
│       └── OaIntegrationProperties.java
└── docs/
    ├── sql/
    │   ├── oa-integration-mysql.sql
    │   └── oa-integration-init-data.sql
    ├── oa-integration-api.md
    ├── oa-integration-deployment-guide.md
    └── oa-integration-usage.md
```

---

## 🗄️ 数据库设计

### 1. AssistantAgent数据库表

#### 用户身份映射表（核心表）
```sql
CREATE TABLE user_identity_mapping (
    id VARCHAR(64) PRIMARY KEY,
    platform_user_id VARCHAR(64) NOT NULL,      -- AssistantAgent用户ID
    system_id VARCHAR(64) NOT NULL,             -- 外部系统ID（如"oa-system"）
    external_user_id VARCHAR(255) NOT NULL,     -- 外部系统用户ID（如OA用户ID）
    external_username VARCHAR(255),             -- 外部系统用户名
    extra_info JSON,                            -- 额外信息（角色、部门等）
    bind_type VARCHAR(20),                      -- 绑定方式: MANUAL/AUTO/OAUTH
    bind_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_system (platform_user_id, system_id)
);
```

#### 外部系统配置表
```sql
CREATE TABLE external_system_config (
    system_id VARCHAR(64) PRIMARY KEY,
    system_name VARCHAR(100) NOT NULL,
    system_type VARCHAR(50),                    -- OA/GOV/ERP/CRM
    api_base_url VARCHAR(500),
    auth_type VARCHAR(50),                      -- API_KEY/OAUTH/BASIC
    auth_config JSON,
    adapter_class VARCHAR(255),
    enabled TINYINT(1) DEFAULT 1
);
```

### 2. OA系统数据库表

#### 用户绑定表
```sql
CREATE TABLE oa_assistant_agent_bind (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    assistant_user_id VARCHAR(64) NOT NULL,     -- AssistantAgent用户ID
    oa_user_id INT UNSIGNED NOT NULL,           -- OA用户ID
    bind_time INT UNSIGNED NOT NULL,            -- 绑定时间戳
    status TINYINT(1) DEFAULT 1,                -- 1启用 0禁用

    UNIQUE KEY uk_assistant_user (assistant_user_id)
);
```

---

## 🔌 API接口设计

### OA系统提供的API

#### 1. 获取用户Token
```
POST /api/oa_integration/get_token

Request:
{
  "assistant_user_id": "U001"
}

Response:
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

#### 2. 获取用户信息
```
GET /api/oa_integration/get_userinfo?oa_user_id=1
Header: Token: eyJ0eXAiOiJKV1QiLCJhbGc...

Response:
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "1",
    "username": "admin",
    "name": "管理员",
    "email": "admin@example.com",
    "mobile": "13800138000",
    "did": "1",
    "dept_name": "总公司",
    "position_id": "1",
    "position_name": "总经理"
  }
}
```

#### 3. 获取用户权限
```
GET /api/oa_integration/get_permissions?oa_user_id=1
Header: Token: eyJ0eXAiOiJKV1QiLCJhbGc...

Response:
{
  "code": 0,
  "msg": "success",
  "data": {
    "user_id": "1",
    "groups": {
      "1": {
        "rules": "1,2,3,4,5...",
        "title": "超级权限角色"
      }
    },
    "data_auth": {
      "office_admin": {
        "uids": "1,2,3"
      }
    }
  }
}
```

---

## 🔄 核心流程

### Token获取流程

```
1. Agent调用: oa_integration(user_id="U001")
   ↓
2. MysqlIdentityMappingService查询绑定关系
   ↓
3. OaTokenCacheManager检查Redis缓存
   ↓ (缓存未命中)
4. OaIntegrationClient调用OA API
   ↓
5. OA系统查询oa_assistant_agent_bind表
   ↓
6. OA生成JWT Token并返回
   ↓
7. Token缓存到Redis（2小时TTL）
   ↓
8. Agent使用Token调用其他API
```

---

## ⚙️ 配置说明

### application.yml配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/assistant_agent
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379

spring.ai.alibaba.oa:
  enabled: true
  api-base-url: http://localhost:8081

spring.ai.alibaba.permission:
  enabled: true
  persistence-type: mysql
```

---

## 🎯 关键技术点

1. **MyBatis Plus**: 数据持久化，自动填充时间字段
2. **Spring Cache**: Redis缓存Token，@Cacheable注解
3. **JWT Token**: 与OA系统一致的认证机制
4. **权限适配器**: 将OA权限转换为标准格式
5. **CodeactTool**: Agent可调用的工具接口

---

## 📝 开发步骤

### 第一阶段：数据库
1. 创建AssistantAgent数据库表
2. 创建OA绑定表
3. 插入初始数据

### 第二阶段：OA系统
1. 创建OaIntegration.php控制器
2. 配置API路由
3. 测试API接口

### 第三阶段：AssistantAgent
1. 创建Entity实体类
2. 创建Mapper接口
3. 创建Service服务
4. 创建OA集成客户端
5. 创建权限适配器
6. 创建CodeactTool工具

### 第四阶段：集成测试
1. 单元测试
2. 集成测试
3. 性能测试

---

## 📊 性能指标

| 指标 | 目标值 |
|------|--------|
| Token获取耗时 | <500ms |
| Token缓存命中率 | >90% |
| API可用性 | >99.9% |

---

## 🔐 安全特性

1. JWT Token认证
2. Token自动过期（2小时）
3. SQL注入防护（预编译）
4. 操作审计日志

---

## 📚 相关文档

- [API接口文档](./oa-integration-api.md)
- [部署指南](./oa-integration-deployment-guide.md)
- [使用示例](./oa-integration-usage.md)

---

**文档版本**: 1.0.0
**创建时间**: 2025-01-21
**作者**: Assistant Agent Team
