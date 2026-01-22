# Assistant Agent 登录认证系统

## 概述

本认证系统为 Assistant Agent 提供了基于 JWT 的完整用户认证功能，包括：

- ✅ 用户登录与 JWT Token 生成
- ✅ 密码加密存储（BCrypt）
- ✅ Token 验证与自动续期
- ✅ 角色与权限管理
- ✅ Spring Security 集成
- ✅ 跨域支持（CORS）
- ✅ 默认管理员账户

## 快速开始

### 1. 初始化数据库

执行 SQL 脚本创建用户表和默认管理员：

```bash
mysql -u root -p assistant_agent < assistant-agent-start/src/main/resources/sql/init_auth.sql
```

或者手动执行：

```sql
USE assistant_agent;

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `roles` VARCHAR(255) DEFAULT NULL,
    `permissions` VARCHAR(500) DEFAULT NULL,
    `enabled` TINYINT(1) DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入默认管理员（用户名/密码: admin/admin）
INSERT INTO `sys_user` (`username`, `password`, `email`, `roles`, `permissions`, `enabled`)
VALUES (
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    'admin@example.com',
    'ADMIN,USER',
    'read,write,delete,execute',
    1
);
```

### 2. 配置 JWT（可选）

在 `application.yml` 中修改 JWT 配置：

```yaml
auth:
  jwt:
    secret: your-secret-key-here  # 生产环境请使用强密钥
    expiration: 86400000           # Token 有效期（毫秒）
    refresh-expiration: 604800000  # 刷新 Token 有效期（毫秒）
```

### 3. 启动应用

```bash
cd assistant-agent-start
mvn clean install
mvn spring-boot:run
```

应用启动时会自动创建默认管理员账户（如果不存在）。

## API 接口

### 登录

**端点**: `POST /api/auth/login`

**请求体**:
```json
{
  "username": "admin",
  "password": "admin",
  "remember": false
}
```

**响应**:
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": "1",
      "username": "admin",
      "email": "admin@example.com",
      "roles": ["ADMIN", "USER"],
      "permissions": ["read", "write", "delete", "execute"]
    },
    "expiresIn": 86400000
  }
}
```

### 验证 Token

**端点**: `GET /api/auth/validate`

**请求头**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

### 登出

**端点**: `POST /api/auth/logout`

**响应**:
```json
{
  "code": 200,
  "message": "登出成功",
  "data": null
}
```

## 前端集成

### 安装依赖

前端已经包含了 `auth.ts` 认证服务，确保已安装：

```bash
npm install axios
```

### 使用示例

```typescript
import authService from '@/services/auth';

// 登录
const response = await authService.login({
  username: 'admin',
  password: 'admin',
  remember: false
});

// 检查认证状态
if (authService.isAuthenticated()) {
  // 已登录
}

// 获取当前用户
const user = authService.getCurrentUser();

// 登出
await authService.logout();
```

### 请求拦截器

所有 API 请求会自动携带 Token：

```typescript
axios.get('/api/agents', {
  headers: {
    Authorization: `Bearer ${authService.getToken()}`
  }
});
```

## 默认账户

- **用户名**: `admin`
- **密码**: `admin`
- **角色**: ADMIN, USER
- **权限**: read, write, delete, execute

⚠️ **生产环境请立即修改默认密码！**

## 安全建议

1. **修改默认密码**: 生产环境必须修改默认管理员密码
2. **使用强 JWT 密钥**: 设置复杂且足够长的 JWT secret
3. **启用 HTTPS**: 生产环境必须使用 HTTPS
4. **配置 CORS**: 限制允许访问的域名
5. **密码策略**: 实现密码强度验证
6. **Token 过期**: 合理设置 Token 有效期

## 项目结构

```
assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/auth/
├── auth/
│   ├── config/
│   │   ├── SecurityConfiguration.java       # Spring Security 配置
│   │   └── AuthInitialization.java          # 认证初始化器
│   ├── controller/
│   │   └── AuthController.java              # 认证控制器
│   ├── dto/
│   │   ├── LoginRequest.java                # 登录请求 DTO
│   │   ├── LoginResponse.java               # 登录响应 DTO
│   │   └── ApiResponse.java                 # 统一响应格式
│   ├── entity/
│   │   └── User.java                        # 用户实体
│   ├── mapper/
│   │   └── UserMapper.java                  # MyBatis Mapper
│   ├── security/
│   │   ├── JwtTokenProvider.java            # JWT 工具类
│   │   └── JwtAuthenticationFilter.java     # JWT 认证过滤器
│   └── service/
│       └── AuthenticationService.java       # 认证服务
```

## 故障排查

### 问题 1: 数据库连接失败

**错误**: `Cannot create PoolableConnectionFactory`

**解决方案**:
- 检查 MySQL 服务是否启动
- 确认 `application.yml` 中的数据库配置正确
- 验证用户名和密码

### 问题 2: Token 验证失败

**错误**: `Invalid JWT token`

**解决方案**:
- 检查 JWT secret 是否一致
- 确认 Token 未过期
- 验证 Token 格式是否正确（Bearer Token）

### 问题 3: CORS 错误

**错误**: `No 'Access-Control-Allow-Origin' header`

**解决方案**:
- 在 `SecurityConfiguration.java` 中配置允许的域名
- 确保前端请求头正确

## 扩展功能

### 添加新用户

```java
User user = new User();
user.setUsername("newuser");
user.setPassword(passwordEncoder.encode("password123"));
user.setEmail("newuser@example.com");
user.setRoles("USER");
user.setPermissions("read,write");
user.setEnabled(true);
userMapper.insert(user);
```

### 自定义权限验证

```java
@PreAuthorize("hasAuthority('delete')")
public void deleteResource(Long id) {
    // 只有拥有 'delete' 权限的用户才能访问
}
```

### 角色验证

```java
@PreAuthorize("hasRole('ADMIN')")
public void adminOnlyOperation() {
    // 只有 ADMIN 角色才能访问
}
```

## 更新日志

### v1.0.0 (2025-01-21)
- ✅ 初始版本发布
- ✅ JWT 认证支持
- ✅ 用户登录/登出
- ✅ Token 验证
- ✅ 角色权限管理
- ✅ Spring Security 集成
- ✅ 默认管理员账户

## 许可证

Apache License 2.0

## 支持

如有问题或建议，请提交 Issue 或 Pull Request。
