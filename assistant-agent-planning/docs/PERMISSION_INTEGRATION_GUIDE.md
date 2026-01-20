# 多系统权限集成使用指南

## 目录

- [概述](#概述)
- [快速开始](#快速开始)
- [核心概念](#核心概念)
- [配置方式](#配置方式)
- [定义带权限的Action](#定义带权限的action)
- [REST API使用](#rest-api使用)
- [自定义扩展](#自定义扩展)
- [示例场景](#示例场景)
- [常见问题](#常见问题)

---

## 概述

多系统权限集成功能允许Assistant Agent平台对接多个外部系统（如OA、政务平台等），并为每个系统提供统一的权限管理。

### 核心特性

- **多系统集成**: 支持同一用户绑定多个外部系统账号
- **权限适配**: 将各系统异构的权限模型转换为统一的标准权限
- **功能权限**: 控制用户可执行哪些Action
- **数据权限**: 自动过滤用户可访问的数据范围
- **自动注入**: 在Action执行前自动注入数据过滤条件

### 架构流程

```
用户请求 → 身份映射 → 权限适配 → 权限检查 → 数据注入 → Action执行
   ↓           ↓           ↓           ↓           ↓           ↓
平台用户ID  外部用户ID  标准权限    允许/拒绝    添加过滤器    执行业务
```

---

## 快速开始

### 1. 启用权限模块

权限模块默认启用。如需禁用：

```yaml
# application.yml
spring.ai.alibaba.codeact.extension.planning.permission:
  enabled: false
```

### 2. 绑定外部系统

```bash
# 绑定用户到OA系统
curl -X POST http://localhost:8080/api/v1/permission/bind \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "U001",
    "systemId": "oa-system",
    "externalUserId": "zhang.san@company.com",
    "externalUsername": "张三",
    "extraInfo": {
      "role": "employee",
      "departmentId": "tech-001"
    }
  }'
```

### 3. 定义带权限的Action

```java
ActionDefinition action = ActionDefinition.builder()
    .actionId("oa:attendance:query-late")
    .name("查询迟到记录")
    .dataPermissionConfig(DataPermissionConfig.builder()
        .enforced(true)  // 启用权限检查
        .filterMapping(Map.of(
            "departmentId", "deptId",  // 权限字段 → Action参数字段
            "userId", "employeeId"
        ))
        .build())
    .build();
```

### 4. 执行Action

```java
Map<String, Object> params = Map.of(
    "platformUserId", "U001",      // 平台用户ID
    "systemId", "oa-system",       // 目标系统
    "date", "2024-01-20"           // 业务参数
);

ExecutionResult result = actionExecutorFactory.execute(action, params, 30);
```

**自动效果**：
- ✅ 检查用户是否有 `oa:attendance:query-late` 权限
- ✅ 根据用户数据权限自动添加过滤条件（如 `deptId=tech-001`）
- ✅ 权限不足时返回错误，阻止执行

---

## 核心概念

### 1. 三层身份架构

```
┌─────────────────┐
│  平台用户 (U001) │  统一身份，跨系统使用
└────────┬────────┘
         │
    ┌────┴────┬─────────────┐
    │         │             │
┌───▼────┐ ┌──▼────┐  ┌────▼────┐
│OA用户  │ │Gov用户│  │CRM用户  │  外部系统账号
│zhang.san│ │320102...│ │...     │
└────────┘ └───────┘  └─────────┘
```

### 2. 标准权限模型 (StandardPermission)

所有外部系统的权限都转换为统一格式：

```java
StandardPermission {
    userId: "zhang.san@company.com",        // 外部系统用户ID
    systemId: "oa-system",                   // 系统标识
    allowedActions: [                        // 功能权限
        "oa:attendance:query-late",
        "oa:task:update-status"
    ],
    dataScope: DEPARTMENT,                   // 数据范围
    filters: {                               // 数据过滤条件
        "departmentId": {
            field: "departmentId",
            operator: "eq",
            value: "tech-001"
        }
    }
}
```

### 3. 数据权限范围 (DataScopeType)

| 范围 | 说明 | 自动注入的过滤器 |
|-----|------|----------------|
| `SELF` | 仅本人数据 | `userId = 当前用户ID` |
| `DEPARTMENT` | 本部门数据 | `departmentId = 用户部门ID` |
| `DEPARTMENT_TREE` | 本部门及子部门 | `departmentIds IN (用户部门树)` |
| `ORGANIZATION` | 全组织数据 | 无过滤 |
| `CUSTOM` | 自定义规则 | 根据filters配置 |

### 4. 权限拦截流程

```java
// ActionExecutorFactory.execute() 内部流程
1. 提取用户上下文
   platformUserId = params.get("platformUserId");
   systemId = params.get("systemId");

2. 权限检查 (PermissionInterceptor)
   if (action.dataPermissionConfig.enforced) {
       permission = getPermission(platformUserId, systemId);

       // 检查功能权限
       if (!permission.allowedActions.contains(action.actionId)) {
           return ExecutionResult.failure("权限不足");
       }

       // 注入数据权限
       params = injectDataFilters(permission, params);
   }

3. 执行Action
   return executor.execute(action, params, timeout);
```

---

## 配置方式

### 1. 启用/禁用权限模块

```yaml
spring.ai.alibaba.codeact.extension.planning.permission:
  enabled: true  # 默认为true
```

### 2. 自动配置的Bean

当权限模块启用时，自动创建以下Bean：

- **OaPermissionAdapter**: OA系统权限适配器
- **GovPermissionAdapter**: 政务平台权限适配器
- **PermissionAdapterRegistry**: 适配器注册中心
- **InMemoryIdentityMappingService**: 身份映射服务（内存实现）
- **DefaultPermissionService**: 权限服务
- **UnifiedChatService**: 统一对话服务
- **PermissionInterceptor**: 权限拦截器
- **PermissionController**: REST API控制器

### 3. 替换默认实现

使用自定义实现替换默认Bean：

```java
@Configuration
public class CustomPermissionConfig {

    // 替换身份映射服务（如使用数据库存储）
    @Bean
    @Primary
    public IdentityMappingService identityMappingService() {
        return new DatabaseIdentityMappingService();
    }

    // 添加自定义权限适配器
    @Bean
    public PermissionAdapter customSystemAdapter() {
        return new CustomSystemPermissionAdapter();
    }
}
```

---

## 定义带权限的Action

### 示例1：查询迟到记录（部门级权限）

```java
ActionDefinition queryLateAction = ActionDefinition.builder()
    .actionId("oa:attendance:query-late")
    .name("查询迟到记录")
    .description("查询员工迟到考勤记录")
    .interfaceBinding(StepDefinition.InterfaceBinding.builder()
        .type("HTTP")
        .config(HttpOptionsConfig.builder()
            .url("https://oa.company.com/api/attendance/late")
            .method("GET")
            .build())
        .build())
    .params(List.of(
        ParamDefinition.builder()
            .name("date")
            .type("string")
            .required(true)
            .description("查询日期")
            .build(),
        ParamDefinition.builder()
            .name("deptId")  // 将被自动注入
            .type("string")
            .required(false)
            .description("部门ID（自动注入）")
            .build()
    ))
    .dataPermissionConfig(DataPermissionConfig.builder()
        .enforced(true)
        .filterMapping(Map.of("departmentId", "deptId"))
        .build())
    .build();
```

**执行时**：
```java
Map<String, Object> params = Map.of(
    "platformUserId", "U002",      // 经理
    "systemId", "oa-system",
    "date", "2024-01-20"
    // deptId 将自动注入为 "tech-001"
);

// 实际执行的HTTP请求：
// GET https://oa.company.com/api/attendance/late?date=2024-01-20&deptId=tech-001
```

### 示例2：更新任务状态（仅本人）

```java
ActionDefinition updateTaskAction = ActionDefinition.builder()
    .actionId("oa:task:update-status")
    .name("更新任务状态")
    .interfaceBinding(StepDefinition.InterfaceBinding.builder()
        .type("HTTP")
        .config(HttpOptionsConfig.builder()
            .url("https://oa.company.com/api/task/update")
            .method("POST")
            .build())
        .build())
    .params(List.of(
        ParamDefinition.builder()
            .name("taskId")
            .type("string")
            .required(true)
            .build(),
        ParamDefinition.builder()
            .name("status")
            .type("string")
            .required(true)
            .build(),
        ParamDefinition.builder()
            .name("employeeId")  // 将被自动注入
            .type("string")
            .required(true)
            .description("员工ID（自动注入）")
            .build()
    ))
    .dataPermissionConfig(DataPermissionConfig.builder()
        .enforced(true)
        .filterMapping(Map.of("userId", "employeeId"))
        .build())
    .build();
```

### 示例3：不需要权限的Action

```java
ActionDefinition publicAction = ActionDefinition.builder()
    .actionId("public:weather:query")
    .name("查询天气")
    // 不设置 dataPermissionConfig，或设置 enforced=false
    .dataPermissionConfig(DataPermissionConfig.builder()
        .enforced(false)
        .build())
    .build();
```

---

## REST API使用

### 1. 获取用户可访问的系统

```bash
GET /api/v1/permission/systems?userId=U001
```

**响应**：
```json
{
  "success": true,
  "data": [
    {
      "systemId": "oa-system",
      "systemName": "OA办公系统",
      "systemType": "OA",
      "externalUserId": "zhang.san@company.com",
      "externalUsername": "张三",
      "bound": true,
      "description": "企业OA办公系统，包含考勤、任务管理等功能"
    },
    {
      "systemId": "gov-platform",
      "systemName": "政务服务平台",
      "systemType": "GOV",
      "externalUserId": "320102199001011234",
      "externalUsername": "张三",
      "bound": true,
      "description": "政务服务平台，提供业务办理、预约等功能"
    }
  ]
}
```

### 2. 绑定外部系统

```bash
POST /api/v1/permission/bind
Content-Type: application/json

{
  "userId": "U001",
  "systemId": "oa-system",
  "externalUserId": "zhang.san@company.com",
  "externalUsername": "张三",
  "extraInfo": {
    "role": "employee",
    "departmentId": "tech-001"
  }
}
```

**响应**：
```json
{
  "success": true,
  "message": "绑定成功"
}
```

### 3. 解绑外部系统

```bash
DELETE /api/v1/permission/unbind?userId=U001&systemId=oa-system
```

### 4. 查询用户权限

```bash
GET /api/v1/permission/info?userId=U001&systemId=oa-system
```

**响应**：
```json
{
  "success": true,
  "data": {
    "systemId": "oa-system",
    "userId": "zhang.san@company.com",
    "allowedActions": [
      "oa:attendance:apply-leave",
      "oa:task:update-status"
    ],
    "dataScope": "SELF",
    "filters": {
      "userId": {
        "field": "userId",
        "operator": "eq",
        "value": "zhang.san@company.com"
      }
    }
  }
}
```

### 5. 检查权限

```bash
POST /api/v1/permission/check
Content-Type: application/json

{
  "userId": "U001",
  "systemId": "oa-system",
  "actionId": "oa:attendance:query-late",
  "context": {}
}
```

**响应（有权限）**：
```json
{
  "success": true,
  "data": {
    "allowed": true,
    "message": "",
    "errorCode": ""
  }
}
```

**响应（无权限）**：
```json
{
  "success": true,
  "data": {
    "allowed": false,
    "message": "您没有执行此操作的权限: oa:attendance:query-late",
    "errorCode": "NO_PERMISSION"
  }
}
```

---

## 自定义扩展

### 1. 实现自定义权限适配器

为新系统创建权限适配器：

```java
@Component
public class CrmPermissionAdapter implements PermissionAdapter {

    @Override
    public String getSystemId() {
        return "crm-system";
    }

    @Override
    public String getSystemName() {
        return "CRM客户管理系统权限适配器";
    }

    @Override
    public StandardPermission adapt(Map<String, Object> context) {
        // 从context中提取CRM系统的权限信息
        String role = (String) context.get("role");
        String region = (String) context.get("region");

        StandardPermission permission = new StandardPermission();
        permission.setSystemId("crm-system");
        permission.setUserId((String) context.get("userId"));

        // 根据角色映射功能权限
        List<String> allowedActions = new ArrayList<>();
        if ("salesman".equals(role)) {
            allowedActions.add("crm:customer:view");
            allowedActions.add("crm:customer:update");
            permission.setDataScope(DataScopeType.SELF);
            permission.addFilter("salesmanId", "eq", context.get("userId"));
        } else if ("manager".equals(role)) {
            allowedActions.add("crm:customer:view");
            allowedActions.add("crm:customer:update");
            allowedActions.add("crm:customer:delete");
            permission.setDataScope(DataScopeType.CUSTOM);
            permission.addFilter("region", "eq", region);
        }

        permission.setAllowedActions(allowedActions);
        return permission;
    }
}
```

### 2. 实现数据库身份映射

替换内存实现为数据库实现：

```java
@Service
@Primary
public class DatabaseIdentityMappingService implements IdentityMappingService {

    @Autowired
    private UserIdentityMappingRepository repository;

    @Override
    public void bindIdentity(String platformUserId, String systemId,
                             String externalUserId, String externalUsername,
                             Map<String, Object> extraInfo) {
        UserIdentityMapping mapping = new UserIdentityMapping();
        mapping.setPlatformUserId(platformUserId);
        mapping.setSystemId(systemId);
        mapping.setExternalUserId(externalUserId);
        mapping.setExternalUsername(externalUsername);
        mapping.setExtraInfo(extraInfo);
        mapping.setBindTime(LocalDateTime.now());

        repository.save(mapping);
    }

    @Override
    public Optional<ExternalIdentity> getExternalIdentity(String platformUserId,
                                                            String systemId) {
        return repository.findByPlatformUserIdAndSystemId(platformUserId, systemId)
                .map(this::toExternalIdentity);
    }

    @Override
    public List<AccessibleSystem> getAccessibleSystems(String platformUserId) {
        List<UserIdentityMapping> mappings = repository.findByPlatformUserId(platformUserId);

        // 从配置加载所有系统
        List<ExternalSystemConfig> allSystems = loadSystemConfigs();

        return allSystems.stream()
                .map(system -> {
                    boolean bound = mappings.stream()
                            .anyMatch(m -> m.getSystemId().equals(system.getSystemId()));

                    AccessibleSystem as = new AccessibleSystem();
                    as.setSystemId(system.getSystemId());
                    as.setSystemName(system.getSystemName());
                    as.setBound(bound);

                    if (bound) {
                        UserIdentityMapping mapping = mappings.stream()
                                .filter(m -> m.getSystemId().equals(system.getSystemId()))
                                .findFirst().orElse(null);
                        if (mapping != null) {
                            as.setExternalUserId(mapping.getExternalUserId());
                            as.setExternalUsername(mapping.getExternalUsername());
                        }
                    }

                    return as;
                })
                .collect(Collectors.toList());
    }

    // ... 其他方法实现
}
```

### 3. 自定义权限检查逻辑

扩展权限服务添加自定义检查：

```java
@Service
@Primary
public class CustomPermissionService extends DefaultPermissionService {

    @Autowired
    private AuditLogService auditLogService;

    public CustomPermissionService(PermissionAdapterRegistry adapterRegistry,
                                   IdentityMappingService identityMappingService) {
        super(adapterRegistry, identityMappingService);
    }

    @Override
    public PermissionCheckResult checkActionPermission(StandardPermission permission,
                                                        String actionId) {
        // 调用父类检查
        PermissionCheckResult result = super.checkActionPermission(permission, actionId);

        // 记录审计日志
        auditLogService.log(AuditLog.builder()
                .userId(permission.getUserId())
                .systemId(permission.getSystemId())
                .actionId(actionId)
                .allowed(result.isAllowed())
                .timestamp(LocalDateTime.now())
                .build());

        // 额外检查：工作时间限制
        if (result.isAllowed() && requiresWorkingHours(actionId)) {
            if (!isWorkingHours()) {
                return PermissionCheckResult.denied(
                        "此操作仅限工作时间执行（9:00-18:00）",
                        "OUTSIDE_WORKING_HOURS");
            }
        }

        return result;
    }

    private boolean requiresWorkingHours(String actionId) {
        return actionId.startsWith("oa:approval:");
    }

    private boolean isWorkingHours() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 0)) &&
               now.isBefore(LocalTime.of(18, 0));
    }
}
```

---

## 示例场景

### 场景1：跨部门查询考勤（经理权限）

**用户**: 李四（技术部经理）
**需求**: 查询技术部所有员工的迟到记录

```java
// 1. 定义Action
ActionDefinition action = ActionDefinition.builder()
    .actionId("oa:attendance:query-late")
    .dataPermissionConfig(DataPermissionConfig.builder()
        .enforced(true)
        .filterMapping(Map.of("departmentId", "deptId"))
        .build())
    .build();

// 2. 执行
Map<String, Object> params = Map.of(
    "platformUserId", "U002",  // 李四
    "systemId", "oa-system",
    "date", "2024-01-20"
);

ExecutionResult result = actionExecutorFactory.execute(action, params, 30);

// 3. 自动效果
// - 权限检查：李四有 oa:attendance:query-late 权限 ✓
// - 数据注入：departmentId = "tech-001" (技术部)
// - HTTP请求：GET /api/attendance/late?date=2024-01-20&deptId=tech-001
```

### 场景2：员工只能查看自己的任务（SELF权限）

**用户**: 张三（普通员工）
**需求**: 查看自己的任务列表

```java
// 1. Action定义
ActionDefinition action = ActionDefinition.builder()
    .actionId("oa:task:query-my-tasks")
    .dataPermissionConfig(DataPermissionConfig.builder()
        .enforced(true)
        .filterMapping(Map.of("userId", "assigneeId"))
        .build())
    .build();

// 2. 执行
Map<String, Object> params = Map.of(
    "platformUserId", "U001",  // 张三
    "systemId", "oa-system",
    "status", "in_progress"
);

// 3. 自动效果
// - 数据注入：assigneeId = "zhang.san@company.com"
// - HTTP请求：GET /api/tasks?status=in_progress&assigneeId=zhang.san@company.com
// - 结果：只返回张三自己的任务
```

### 场景3：权限不足被拒绝

**用户**: 张三（普通员工）
**尝试**: 查询部门所有人的迟到记录（需要经理权限）

```java
Map<String, Object> params = Map.of(
    "platformUserId", "U001",  // 张三
    "systemId", "oa-system",
    "actionId", "oa:attendance:query-late"
);

ExecutionResult result = actionExecutorFactory.execute(action, params, 30);

// 结果
result.isSuccess() == false
result.getError() == "权限不足: 您没有执行此操作的权限: oa:attendance:query-late"
```

### 场景4：多系统切换（同一用户）

**用户**: 王五（同时使用OA和政务平台）
**场景**: 上午处理OA审批，下午办理政务业务

```java
// 上午：OA系统审批
Map<String, Object> oaParams = Map.of(
    "platformUserId", "U003",
    "systemId", "oa-system",
    "actionId", "oa:approval:submit"
);
actionExecutorFactory.execute(approvalAction, oaParams, 30);
// 权限：director 角色 → ORGANIZATION 范围

// 下午：政务平台办理业务
Map<String, Object> govParams = Map.of(
    "platformUserId", "U003",
    "systemId", "gov-platform",
    "actionId", "gov:appointment:set-limit"
);
actionExecutorFactory.execute(appointmentAction, govParams, 30);
// 权限：leader 角色 → DEPARTMENT_TREE 范围

// 同一平台用户，不同系统，自动切换权限
```

---

## 常见问题

### Q1: 权限检查失败，如何排查？

**A**: 按以下步骤排查：

1. 检查用户是否绑定了系统
```bash
curl http://localhost:8080/api/v1/permission/systems?userId=U001
```

2. 检查用户在该系统的权限
```bash
curl "http://localhost:8080/api/v1/permission/info?userId=U001&systemId=oa-system"
```

3. 检查Action是否正确配置权限
```java
// 确保 dataPermissionConfig.enforced = true
// 确保 filterMapping 正确
```

4. 查看日志
```
# 搜索关键字
PermissionInterceptor#checkPermission
DefaultPermissionService#checkActionPermission
```

### Q2: 数据权限未生效，参数没有注入？

**A**: 检查以下几点：

1. **filterMapping是否正确**
```java
// 错误：字段名不匹配
.filterMapping(Map.of("departmentId", "deptId"))
// Action参数名是 "departmentId"，但应该是 "deptId"

// 正确
.filterMapping(Map.of("departmentId", "deptId"))
// StandardPermission.filters["departmentId"] → Action.params["deptId"]
```

2. **Action参数是否定义**
```java
// 必须在Action的params中定义对应字段
.params(List.of(
    ParamDefinition.builder()
        .name("deptId")  // 对应 filterMapping 的值
        .type("string")
        .required(false)  // 可选，由系统注入
        .build()
))
```

3. **用户上下文是否传递**
```java
// 必须包含 platformUserId 和 systemId
Map<String, Object> params = Map.of(
    "platformUserId", "U001",  // ✓
    "systemId", "oa-system",   // ✓
    "date", "2024-01-20"
);
```

### Q3: 如何为Action禁用权限检查？

**A**: 两种方式：

**方式1**: 不设置 `dataPermissionConfig`
```java
ActionDefinition action = ActionDefinition.builder()
    .actionId("public:action")
    // 不设置 dataPermissionConfig
    .build();
```

**方式2**: 设置 `enforced = false`
```java
.dataPermissionConfig(DataPermissionConfig.builder()
    .enforced(false)
    .build())
```

### Q4: 权限模块性能如何？会影响执行速度吗？

**A**: 性能影响很小：

- **权限检查**: O(1) 哈希表查找，< 1ms
- **数据注入**: O(n) 遍历过滤器，n通常 < 5，< 1ms
- **总开销**: < 5ms

**优化建议**：
1. 使用缓存（Redis）存储权限信息，避免重复查询
2. 异步记录审计日志
3. 批量操作时复用权限对象

### Q5: 如何处理复杂的自定义权限规则？

**A**: 在PermissionAdapter中实现自定义逻辑：

```java
@Override
public StandardPermission adapt(Map<String, Object> context) {
    StandardPermission permission = new StandardPermission();

    String role = (String) context.get("role");
    String level = (String) context.get("level");
    String region = (String) context.get("region");

    // 复杂规则：高级销售可以查看整个区域
    if ("salesman".equals(role) && "senior".equals(level)) {
        permission.setDataScope(DataScopeType.CUSTOM);
        permission.addFilter("region", "eq", region);
        permission.addFilter("level", "gte", "regular");
    }
    // 普通销售只能看自己的
    else if ("salesman".equals(role)) {
        permission.setDataScope(DataScopeType.SELF);
        permission.addFilter("salesmanId", "eq", context.get("userId"));
    }

    return permission;
}
```

### Q6: 如何实现动态权限（实时从外部API获取）？

**A**: 在 `IdentityMappingService` 或 `PermissionAdapter` 中调用外部API：

```java
@Override
public StandardPermission adapt(Map<String, Object> context) {
    String userId = (String) context.get("userId");

    // 实时调用外部权限API
    Map<String, Object> externalPermission = restTemplate.getForObject(
        "https://api.external.com/permissions/" + userId,
        Map.class
    );

    // 转换为StandardPermission
    return convertToStandardPermission(externalPermission);
}
```

**建议**: 添加缓存避免频繁调用外部API：

```java
@Cacheable(value = "permissions", key = "#systemId + ':' + #userId")
public StandardPermission adapt(String systemId, String userId, Map<String, Object> context) {
    // ... 调用外部API
}
```

### Q7: 权限系统如何与现有Spring Security集成？

**A**: 两者可以共存：

- **Spring Security**: 管理认证（Authentication）和Web层授权
- **Permission System**: 管理业务层权限和数据权限

```java
@RestController
public class MyController {

    @GetMapping("/api/data")
    @PreAuthorize("hasRole('USER')")  // Spring Security认证
    public ResponseEntity<?> getData(@RequestParam String userId,
                                      @RequestParam String systemId) {
        // 业务权限检查由Permission System自动处理
        ExecutionResult result = actionExecutorFactory.execute(
            action,
            Map.of("platformUserId", userId, "systemId", systemId),
            30
        );

        return ResponseEntity.ok(result);
    }
}
```

---

## 最佳实践

### 1. Action命名规范

使用 `{system}:{module}:{operation}` 格式：

```
✓ oa:attendance:query-late
✓ oa:task:update-status
✓ gov:appointment:set-limit

✗ queryLate
✗ updateTask
```

### 2. 权限粒度设计

- **功能权限**: 粗粒度（模块级）
- **数据权限**: 细粒度（记录级）

```java
// 好的设计
allowedActions: ["oa:attendance:manage"]  // 粗粒度
dataScope: DEPARTMENT                      // 细粒度过滤

// 不好的设计
allowedActions: [
    "oa:attendance:query:employee:001",   // 太细
    "oa:attendance:query:employee:002"
]
```

### 3. 错误处理

```java
try {
    ExecutionResult result = actionExecutorFactory.execute(action, params, 30);

    if (!result.isSuccess()) {
        if (result.getError().contains("权限不足")) {
            // 引导用户绑定系统或申请权限
            return guideUserToBindSystem();
        } else {
            // 其他业务错误
            return handleBusinessError(result);
        }
    }

    return result;
} catch (Exception e) {
    logger.error("Action execution failed", e);
    return ExecutionResult.failure("系统错误");
}
```

### 4. 测试策略

```java
@Test
void testActionWithPermission() {
    // 1. 准备：绑定用户
    identityMappingService.bindIdentity(...);

    // 2. 执行：带权限上下文
    Map<String, Object> params = Map.of(
        "platformUserId", "test-user",
        "systemId", "test-system"
    );
    ExecutionResult result = actionExecutorFactory.execute(action, params, 30);

    // 3. 验证：权限检查通过
    assertTrue(result.isSuccess());

    // 4. 验证：数据过滤器已注入
    verify(httpExecutor).execute(
        argThat(a -> a.getParams().containsKey("deptId"))
    );
}
```

---

## 总结

多系统权限集成为Assistant Agent提供了：
- ✅ 统一的权限管理模型
- ✅ 自动化的权限检查和数据过滤
- ✅ 灵活的扩展机制
- ✅ 开箱即用的REST API

通过合理使用权限配置和扩展点，可以快速对接各种外部系统，实现安全可控的跨系统操作。

**相关文档**：
- [多系统权限设计文档](./2026-01-20-multi-system-permission-design.md)
- [Planning模块文档](./PLANNING_MODULE.md)
- [ActionDefinition API文档](../assistant-agent-planning-api/README.md)
