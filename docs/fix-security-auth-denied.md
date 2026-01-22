# 修复：Spring Security Access Denied

## 🐛 问题原因

**AuthorizationDeniedException**: 访问 Planning API 端点时被 Spring Security 拦截。

Spring Security 配置要求所有 `/api/**` 请求都需要 JWT 认证，但 ChatUI 前端调用时没有提供认证信息。

## ✅ 解决方案

### 已修复

更新 `SecurityConfiguration.java`，将 Planning API 端点添加到 permitAll 列表：

```java
.requestMatchers(
    "/api/auth/**",
    "/api/debug/**",
    "/api/v1/**",       // ← 新增：Planning API endpoints (for testing)
    "/actuator/**",
    "/error",
    "/swagger-ui/**",
    "/v3/api-docs/**"
).permitAll()
```

### 受影响的端点

现在以下端点可以无需 JWT 认证访问：
- `/api/v1/actions/**` - Action CRUD 和匹配接口
- `/api/v1/plans/**` - Plan 生成和执行接口
- `/api/v1/permissions/**` - 权限相关接口

---

## 🚀 部署步骤

1. **重启应用**
   ```bash
   cd assistant-agent-start
   mvn spring-boot:run
   ```

2. **测试**
   - 访问 ChatUI: `http://localhost:8080/chatui/index.html`
   - 输入测试消息: "我想明天请假一天"

---

## 📋 安全说明

⚠️ **仅用于开发/测试环境**

在生产环境中，建议：
1. 移除 `/api/v1/**` 的 permitAll 配置
2. 为 ChatUI 实现 JWT 认证流程
3. 添加适当的授权规则

---

## 🎯 验证

重启后，测试 OA 请假功能应该能正常工作：

```
用户: 我想明天请假一天
系统: [识别意图] → [参数收集] → [InternalExecutor] → [OaSystemHandler] → OA API ✅
```

---

**文档版本**: 1.0.0
**创建时间**: 2026-01-22
**作者**: Assistant Agent Team
