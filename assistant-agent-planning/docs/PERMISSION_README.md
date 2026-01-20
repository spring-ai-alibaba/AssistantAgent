# 多系统权限集成 - 文档导航

## 📚 文档列表

### 1. [权限集成使用指南](./PERMISSION_INTEGRATION_GUIDE.md) ⭐ **推荐从这里开始**

完整的使用手册，包含：
- ✅ 快速开始（5分钟上手）
- ✅ 核心概念详解
- ✅ 配置方式和自定义扩展
- ✅ REST API完整文档
- ✅ 4个真实场景示例
- ✅ 7个常见问题解答
- ✅ 最佳实践建议

**适合人群**: 开发者、架构师、运维人员

---

### 2. [快速开始示例代码](./examples/PermissionQuickStartExample.java)

5个可运行的完整示例：
- 示例1: 员工查询自己的考勤记录（SELF权限）
- 示例2: 经理查询部门考勤记录（DEPARTMENT权限）
- 示例3: 权限不足被拒绝
- 示例4: 多系统切换（同一用户多系统）
- 示例5: 公开Action（无需权限）

**使用方式**:
```java
@Autowired
private PermissionQuickStartExample example;

// 运行所有示例
example.runAllExamples();

// 或运行单个示例
example.example1_EmployeeQueryOwnAttendance();
```

---

### 3. [多系统权限设计文档](./2026-01-20-multi-system-permission-design.md)

技术设计方案，包含：
- 业务场景和需求分析
- 架构设计和技术选型
- 数据库表设计
- 接口设计
- 14个实现任务清单

**适合人群**: 架构师、高级开发者

---

## 🚀 5分钟快速上手

### 步骤1: 启用权限模块（默认已启用）

```yaml
# application.yml
spring.ai.alibaba.codeact.extension.planning.permission:
  enabled: true  # 默认为true
```

### 步骤2: 绑定用户到外部系统

```bash
curl -X POST http://localhost:8080/api/v1/permission/bind \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "U001",
    "systemId": "oa-system",
    "externalUserId": "zhang.san@company.com",
    "externalUsername": "张三",
    "extraInfo": {"role": "employee", "departmentId": "tech-001"}
  }'
```

### 步骤3: 定义带权限的Action

```java
ActionDefinition action = ActionDefinition.builder()
    .actionId("oa:attendance:query-my-records")
    .dataPermissionConfig(DataPermissionConfig.builder()
        .enforced(true)  // 启用权限检查
        .filterMapping(Map.of("userId", "employeeId"))
        .build())
    .build();
```

### 步骤4: 执行Action（自动权限检查）

```java
Map<String, Object> params = Map.of(
    "platformUserId", "U001",
    "systemId", "oa-system",
    "date", "2024-01-20"
);

ExecutionResult result = actionExecutorFactory.execute(action, params, 30);
// ✓ 自动检查权限
// ✓ 自动注入数据过滤条件
```

---

## 🏗️ 架构概览

```
用户请求
   ↓
ActionExecutorFactory.execute()
   ↓
PermissionInterceptor (权限拦截)
   ├─→ 提取用户上下文 (platformUserId, systemId)
   ├─→ 获取标准权限 (PermissionService)
   ├─→ 检查功能权限 (是否允许执行此Action)
   │   ├─ 允许 → 继续
   │   └─ 拒绝 → 返回"权限不足"错误
   ├─→ 注入数据权限 (添加过滤条件到params)
   │   └─ SELF → userId过滤
   │   └─ DEPARTMENT → departmentId过滤
   │   └─ ORGANIZATION → 无过滤
   ↓
ActionExecutor.execute() (实际执行业务)
   ↓
返回结果
```

---

## 📊 REST API概览

| 端点 | 方法 | 功能 | 示例 |
|-----|------|------|------|
| `/api/v1/permission/systems` | GET | 获取用户可访问的系统列表 | `?userId=U001` |
| `/api/v1/permission/bind` | POST | 绑定用户到外部系统 | `{userId, systemId, ...}` |
| `/api/v1/permission/unbind` | DELETE | 解绑外部系统 | `?userId=U001&systemId=oa` |
| `/api/v1/permission/info` | GET | 查询用户权限 | `?userId=U001&systemId=oa` |
| `/api/v1/permission/check` | POST | 检查Action权限 | `{userId, systemId, actionId}` |

**详细API文档**: 参见 [权限集成使用指南 - REST API使用](./PERMISSION_INTEGRATION_GUIDE.md#rest-api使用)

---

## 🎯 核心特性

### ✅ 多系统集成
- 一个平台用户可绑定多个外部系统账号
- 自动根据 `systemId` 切换权限
- 支持异构系统的权限模型适配

### ✅ 双重权限控制
- **功能权限**: 控制用户可执行哪些Action
- **数据权限**: 自动过滤用户可访问的数据范围

### ✅ 自动注入
- 根据用户的数据权限自动添加过滤条件
- 开发者无需手动处理权限逻辑
- 支持 `SELF`, `DEPARTMENT`, `DEPARTMENT_TREE`, `ORGANIZATION` 等范围

### ✅ 灵活扩展
- SPI接口支持自定义权限适配器
- 可替换默认的身份映射服务
- 支持自定义权限检查逻辑

---

## 🔧 自定义扩展

### 添加新系统适配器

```java
@Component
public class CustomSystemAdapter implements PermissionAdapter {
    @Override
    public String getSystemId() {
        return "custom-system";
    }

    @Override
    public StandardPermission adapt(Map<String, Object> context) {
        // 实现权限转换逻辑
        // ...
        return permission;
    }
}
```

### 替换身份映射服务

```java
@Service
@Primary
public class DatabaseIdentityMappingService implements IdentityMappingService {
    // 使用数据库存储用户身份映射
    // ...
}
```

**详细扩展指南**: 参见 [权限集成使用指南 - 自定义扩展](./PERMISSION_INTEGRATION_GUIDE.md#自定义扩展)

---

## 💡 使用示例

### 场景1: 员工只能查看自己的数据

```java
// 定义Action
.dataPermissionConfig(DataPermissionConfig.builder()
    .enforced(true)
    .filterMapping(Map.of("userId", "employeeId"))
    .build())

// 执行
params = Map.of("platformUserId", "U001", "systemId", "oa");
// 自动注入: employeeId = "zhang.san@company.com"
```

### 场景2: 经理可以查看整个部门的数据

```java
// 定义Action
.dataPermissionConfig(DataPermissionConfig.builder()
    .enforced(true)
    .filterMapping(Map.of("departmentId", "deptId"))
    .build())

// 执行
params = Map.of("platformUserId", "U002", "systemId", "oa");
// 自动注入: deptId = "tech-001"
```

### 场景3: 权限不足被拒绝

```java
// 普通员工尝试执行需要经理权限的Action
ExecutionResult result = actionExecutorFactory.execute(
    managerOnlyAction,
    Map.of("platformUserId", "U001", "systemId", "oa"),
    30
);

// 结果: result.isSuccess() == false
// 错误: "权限不足: 您没有执行此操作的权限"
```

**更多示例**: 参见 [快速开始示例代码](./examples/PermissionQuickStartExample.java)

---

## 📝 测试

### 运行权限集成测试

```bash
mvn test -pl assistant-agent-planning/assistant-agent-planning-core \
  -Dtest=MultiSystemPermissionIntegrationTest
```

**测试覆盖**:
- ✅ OA系统权限测试（4个）
- ✅ 政务平台权限测试（3个）
- ✅ 跨系统测试（4个）
- ✅ 数据权限注入测试（3个）
- ✅ 身份绑定测试（2个）
- ✅ 统一对话服务测试（1个）

**共17个测试全部通过**

---

## 🐛 常见问题

### Q: 权限检查失败怎么办？

**A**: 按以下顺序排查：
1. 检查用户是否绑定了系统: `GET /api/v1/permission/systems?userId=xxx`
2. 检查用户权限: `GET /api/v1/permission/info?userId=xxx&systemId=xxx`
3. 检查Action配置: `dataPermissionConfig.enforced = true`
4. 查看日志: 搜索 `PermissionInterceptor#checkPermission`

### Q: 数据权限未注入？

**A**: 检查：
1. `filterMapping` 是否正确: `Map.of("权限字段", "Action参数字段")`
2. Action参数是否定义了对应字段
3. 是否传递了 `platformUserId` 和 `systemId`

### Q: 如何禁用某个Action的权限检查？

**A**: 两种方式：
```java
// 方式1: 不设置 dataPermissionConfig
ActionDefinition.builder().build()

// 方式2: 设置 enforced = false
.dataPermissionConfig(DataPermissionConfig.builder()
    .enforced(false)
    .build())
```

**更多问题**: 参见 [权限集成使用指南 - 常见问题](./PERMISSION_INTEGRATION_GUIDE.md#常见问题)

---

## 📖 相关文档

- [Planning模块文档](./PLANNING_MODULE.md)
- [ActionDefinition API文档](../assistant-agent-planning-api/README.md)
- [参数收集流程文档](./PARAM_COLLECTION_GUIDE.md)
- [Assistant Agent主文档](../../README.md)

---

## 🤝 贡献

发现问题或有改进建议？欢迎：
- 提交Issue
- 提交Pull Request
- 联系开发团队

---

## 📄 许可证

Apache License 2.0

---

**开始使用**: 阅读 [权限集成使用指南](./PERMISSION_INTEGRATION_GUIDE.md) 📘
