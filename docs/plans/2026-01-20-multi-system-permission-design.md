# 多系统集成与权限体系设计文档

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 设计一套完整的多系统集成方案，支持平台用户通过统一入口操作多个外部系统（OA、政务平台等），并实现灵活的权限适配机制。

**Architecture:** 采用"平台用户体系 + 身份映射层 + 权限适配层"三层架构，平台定义标准权限模型，通过适配器将各客户系统的权限转换为统一格式。

**Tech Stack:** Spring Boot 3.x, Spring Security, MyBatis-Plus, MySQL

---

## 1. 背景与目标

### 1.1 业务背景

- 平台需要对接多个客户系统（OA系统、政务平台等）
- 每个客户系统有独立的用户体系和权限模型
- 用户登录平台后，可以操作已绑定的多个外部系统
- 需要支持数据权限（看什么数据）和功能权限（做什么操作）

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| 统一入口 | 用户只需登录平台一次，即可操作多个系统 |
| 权限隔离 | 不同系统、不同角色的权限互不干扰 |
| 灵活适配 | 支持不同客户系统的权限模型转换 |
| 易于扩展 | 新增系统只需实现适配器即可 |
| Demo友好 | 能快速演示多系统集成和权限控制效果 |

### 1.3 Demo场景

**OA系统场景：**

| 角色 | 模块 | 操作 | 数据范围 |
|------|------|------|----------|
| 员工 | 考勤 | 发起请假流程 | 仅自己 |
| 员工 | 任务 | 更新任务状态 | 仅分配给自己的任务 |
| 管理者 | 考勤 | 查询迟到早退 | 本部门员工 |
| 管理者 | 任务 | 查询任务进度 | 本部门任务 |
| 大领导 | 考勤 | 查询考勤统计 | 全公司 |
| 大领导 | 任务 | 查询任务执行情况 | 全公司 |

**政务服务平台场景：**

| 角色 | 模块 | 操作 | 数据范围 |
|------|------|------|----------|
| 群众 | 业务办理 | 查询办理流程 | 公开信息 |
| 群众 | 预约 | 发起预约 | 仅自己的预约 |
| 业务处理人员 | 预约 | 设置预约限额 | 本业务板块 |
| 业务处理人员 | 内容管理 | 发布文章 | 本栏目 |
| 分管领导 | 统计 | 查询预约/办理情况 | 分管板块 |
| 分管领导 | 统计 | 查询满意度 | 分管板块 |

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Assistant Agent Platform                    │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    平台用户体系                             │ │
│  │  - 统一登录认证 (JWT)                                       │ │
│  │  - 平台用户管理                                             │ │
│  │  - 平台角色管理                                             │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│                              ▼                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   用户身份映射层                            │ │
│  │  - 平台用户 ↔ 外部系统用户 映射                            │ │
│  │  - 支持手动绑定、OAuth授权、自动匹配                       │ │
│  │  - 多系统身份统一管理                                       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│                              ▼                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    权限适配层                               │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │ │
│  │  │ OA Adapter  │  │ Gov Adapter │  │ ERP Adapter │  ...   │ │
│  │  └─────────────┘  └─────────────┘  └─────────────┘        │ │
│  │  - 将外部系统权限转换为平台标准格式                        │ │
│  │  - 每个系统一个适配器                                      │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│                              ▼                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                 统一权限模型 (StandardPermission)          │ │
│  │  - DataScope: SELF / DEPARTMENT / ORGANIZATION / CUSTOM    │ │
│  │  - Actions: 标准化Action定义                               │ │
│  │  - Filters: 统一的数据过滤条件                             │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│                              ▼                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    执行层                                   │ │
│  │  - 功能权限校验 (Action级别)                               │ │
│  │  - 数据权限注入 (自动添加过滤条件)                         │ │
│  │  - Action执行                                              │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            ▼                                     ▼
     ┌─────────────┐                      ┌─────────────┐
     │   OA系统    │                      │  政务平台   │
     │ 用户体系A   │                      │ 用户体系B   │
     └─────────────┘                      └─────────────┘
```

### 2.2 请求处理流程

```
用户请求: "查询今天谁迟到了"
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. JWT认证 → 获取平台用户ID                                      │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. 识别目标系统 → oa-system                                      │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. 身份映射 → 获取外部系统身份 (zhang.san@company.com)           │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. 获取外部权限 → 调用OA系统权限接口或从请求context获取          │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. 权限适配 → OaPermissionAdapter.adapt() → StandardPermission  │
│    {                                                            │
│      allowedActions: ["oa:attendance:query-late"],              │
│      dataScope: DEPARTMENT,                                     │
│      filters: { departmentId: "tech-001" }                      │
│    }                                                            │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. 意图识别 → 匹配Action (仅在allowedActions范围内匹配)          │
│    识别结果: oa:attendance:query-late                           │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 7. 功能权限校验 → 检查Action是否在allowedActions中               │
│    "oa:attendance:query-late" ∈ allowedActions? ✓               │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 8. 参数收集 → 用户参数: { date: "2024-01-20" }                   │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 9. 数据权限注入 → 自动添加过滤条件                               │
│    最终参数: { date: "2024-01-20", departmentId: "tech-001" }   │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ 10. 执行Action → 调用OA系统API                                   │
│     GET /api/attendance/late?date=2024-01-20&departmentId=tech-001
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
    返回结果（已按权限过滤）
```

---

## 3. 数据模型设计

### 3.1 平台用户模型

```java
/**
 * 平台用户
 */
public class PlatformUser {
    private String id;           // 平台用户ID
    private String username;     // 登录名
    private String passwordHash; // 密码哈希
    private String name;         // 姓名
    private String phone;        // 手机号
    private String email;        // 邮箱
    private String status;       // 状态: ACTIVE/DISABLED
    private List<String> roles;  // 平台角色
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.2 外部系统配置

```java
/**
 * 外部系统配置
 */
public class ExternalSystemConfig {
    private String systemId;          // 系统ID (唯一标识)
    private String systemName;        // 系统名称
    private String systemType;        // 系统类型: OA/GOV/ERP/CRM
    private String apiBaseUrl;        // API基础地址
    private String authType;          // 认证类型: API_KEY/OAUTH/BASIC
    private Map<String, Object> authConfig;  // 认证配置
    private String adapterClass;      // 权限适配器类名
    private boolean enabled;          // 是否启用
    private LocalDateTime createdAt;
}
```

### 3.3 用户身份映射

```java
/**
 * 用户身份映射 - 核心模型
 * 建立平台用户与外部系统用户的关联
 */
public class UserIdentityMapping {
    private String id;                // 映射ID
    private String platformUserId;    // 平台用户ID
    private String systemId;          // 外部系统ID
    private String externalUserId;    // 外部系统用户ID
    private String externalUsername;  // 外部系统用户名（显示用）
    private Map<String, Object> extraInfo;  // 额外信息（部门、角色等）
    private String bindType;          // 绑定方式: MANUAL/AUTO/OAUTH
    private LocalDateTime bindTime;   // 绑定时间
}
```

### 3.4 标准权限模型

```java
/**
 * 平台标准权限模型
 * 所有外部系统的权限都会被适配为此格式
 */
public class StandardPermission {
    private String userId;                    // 外部系统用户ID
    private String systemId;                  // 系统ID
    private List<String> allowedActions;      // 允许的操作列表
    private DataScopeType dataScope;          // 数据范围类型
    private Map<String, DataFilter> filters;  // 数据过滤条件
}

/**
 * 数据范围类型
 */
public enum DataScopeType {
    SELF,              // 仅自己的数据
    DEPARTMENT,        // 本部门数据
    DEPARTMENT_TREE,   // 本部门及下级部门数据
    ORGANIZATION,      // 全组织数据
    CUSTOM             // 自定义范围
}

/**
 * 数据过滤条件
 */
public class DataFilter {
    private String field;      // 过滤字段
    private String operator;   // 操作符: eq, ne, in, not_in, between, like
    private Object value;      // 过滤值
}
```

### 3.5 Action数据权限配置

```java
/**
 * Action的数据权限配置
 * 声明Action的哪些参数受权限控制
 */
public class DataPermissionConfig {
    // 权限过滤字段映射
    // key: StandardPermission中的filter字段名
    // value: Action参数中的字段名
    private Map<String, String> filterMapping;

    // 数据范围对应的字段
    private String scopeField;

    // 是否强制校验（true=必须有权限才能执行）
    private boolean enforced = true;
}
```

---

## 4. 接口设计

### 4.1 权限适配器接口

```java
/**
 * 权限适配器接口
 * 每个外部系统实现一个适配器，将系统权限转换为标准格式
 */
public interface PermissionAdapter {

    /**
     * 获取支持的系统ID
     */
    String getSystemId();

    /**
     * 将外部系统的原始权限上下文转换为标准权限格式
     *
     * @param rawContext 原始上下文（包含用户信息、角色、部门等）
     * @return 标准权限对象
     */
    StandardPermission adapt(Map<String, Object> rawContext);

    /**
     * 获取适配器优先级（用于多适配器场景）
     */
    default int getOrder() {
        return 0;
    }
}
```

### 4.2 身份映射服务接口

```java
/**
 * 用户身份映射服务
 */
public interface IdentityMappingService {

    /**
     * 获取用户在指定系统的外部身份
     */
    Optional<ExternalIdentity> getExternalIdentity(String platformUserId, String systemId);

    /**
     * 获取用户可访问的所有系统
     */
    List<AccessibleSystem> getAccessibleSystems(String platformUserId);

    /**
     * 绑定外部系统身份
     */
    void bindIdentity(String platformUserId, String systemId,
                      String externalUserId, Map<String, Object> extraInfo);

    /**
     * 解绑外部系统身份
     */
    void unbindIdentity(String platformUserId, String systemId);

    /**
     * 检查用户是否已绑定指定系统
     */
    boolean isBound(String platformUserId, String systemId);
}
```

### 4.3 权限服务接口

```java
/**
 * 权限服务
 */
public interface PermissionService {

    /**
     * 校验功能权限 - 用户能否执行指定Action
     */
    PermissionCheckResult checkActionPermission(StandardPermission permission, String actionId);

    /**
     * 注入数据权限 - 将权限过滤条件注入到Action参数中
     */
    Map<String, Object> injectDataPermission(StandardPermission permission,
                                              ActionDefinition action,
                                              Map<String, Object> userParams);

    /**
     * 获取用户在指定系统的标准权限
     */
    StandardPermission getPermission(String platformUserId, String systemId,
                                     Map<String, Object> context);
}
```

---

## 5. 数据库设计

### 5.1 表结构

```sql
-- ============================================================================
-- 平台用户表
-- ============================================================================
CREATE TABLE platform_user (
    id VARCHAR(64) PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(100) NOT NULL COMMENT '登录名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    name VARCHAR(100) COMMENT '姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_username (username),
    INDEX idx_phone (phone),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户表';

-- ============================================================================
-- 平台用户角色关联表
-- ============================================================================
CREATE TABLE platform_user_role (
    id VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_user_role (user_id, role_code),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户角色关联表';

-- ============================================================================
-- 外部系统配置表
-- ============================================================================
CREATE TABLE external_system_config (
    system_id VARCHAR(64) PRIMARY KEY COMMENT '系统ID',
    system_name VARCHAR(100) NOT NULL COMMENT '系统名称',
    system_type VARCHAR(50) COMMENT '系统类型: OA/GOV/ERP/CRM',
    api_base_url VARCHAR(500) COMMENT 'API基础地址',
    auth_type VARCHAR(50) COMMENT '认证类型: API_KEY/OAUTH/BASIC',
    auth_config JSON COMMENT '认证配置',
    adapter_class VARCHAR(255) COMMENT '权限适配器类名',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部系统配置表';

-- ============================================================================
-- 用户身份映射表（核心表）
-- ============================================================================
CREATE TABLE user_identity_mapping (
    id VARCHAR(64) PRIMARY KEY COMMENT '映射ID',
    platform_user_id VARCHAR(64) NOT NULL COMMENT '平台用户ID',
    system_id VARCHAR(64) NOT NULL COMMENT '外部系统ID',
    external_user_id VARCHAR(255) NOT NULL COMMENT '外部系统用户ID',
    external_username VARCHAR(255) COMMENT '外部系统用户名',
    extra_info JSON COMMENT '额外信息（部门、角色等）',
    bind_type VARCHAR(20) COMMENT '绑定方式: MANUAL/AUTO/OAUTH',
    bind_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',

    UNIQUE KEY uk_user_system (platform_user_id, system_id),
    INDEX idx_platform_user (platform_user_id),
    INDEX idx_external_user (system_id, external_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户身份映射表';

-- ============================================================================
-- Action数据权限配置表
-- ============================================================================
CREATE TABLE action_permission_config (
    id VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    action_id VARCHAR(100) NOT NULL COMMENT 'Action ID',
    filter_mapping JSON COMMENT '过滤字段映射',
    scope_field VARCHAR(100) COMMENT '数据范围字段',
    enforced TINYINT(1) DEFAULT 1 COMMENT '是否强制校验',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_action_id (action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Action数据权限配置表';
```

### 5.2 初始化数据

```sql
-- ============================================================================
-- 初始化外部系统配置
-- ============================================================================
INSERT INTO external_system_config (system_id, system_name, system_type, api_base_url, auth_type, adapter_class, enabled) VALUES
('oa-system', 'OA办公系统', 'OA', 'http://localhost:8081/api', 'API_KEY', 'com.alibaba.assistant.agent.planning.permission.adapter.OaPermissionAdapter', 1),
('gov-platform', '政务服务平台', 'GOV', 'http://localhost:8082/api', 'API_KEY', 'com.alibaba.assistant.agent.planning.permission.adapter.GovPermissionAdapter', 1);

-- ============================================================================
-- 初始化Demo用户
-- ============================================================================
INSERT INTO platform_user (id, username, password_hash, name, phone, status) VALUES
('U001', 'zhangsan', '$2a$10$xxxxx', '张三', '13800000001', 'ACTIVE'),
('U002', 'lisi', '$2a$10$xxxxx', '李四', '13800000002', 'ACTIVE'),
('U003', 'wangwu', '$2a$10$xxxxx', '王五', '13800000003', 'ACTIVE');

-- ============================================================================
-- 初始化身份映射（Demo数据）
-- ============================================================================
-- 张三：OA系统员工，政务平台群众
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M001', 'U001', 'oa-system', 'zhang.san@company.com', '张三', '{"role": "employee", "deptId": "tech-001", "deptName": "技术部"}', 'MANUAL'),
('M002', 'U001', 'gov-platform', '320102199001011234', '张三', '{"userType": "citizen"}', 'MANUAL');

-- 李四：OA系统经理
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M003', 'U002', 'oa-system', 'li.si@company.com', '李四', '{"role": "manager", "deptId": "tech-001", "deptName": "技术部"}', 'MANUAL');

-- 王五：OA系统大领导，政务平台分管领导
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M004', 'U003', 'oa-system', 'wang.wu@company.com', '王五', '{"role": "director"}', 'MANUAL'),
('M005', 'U003', 'gov-platform', 'leader_001', '王五', '{"userType": "leader", "bureauId": "civil-affairs"}', 'MANUAL');

-- ============================================================================
-- 初始化Action权限配置
-- ============================================================================
INSERT INTO action_permission_config (id, action_id, filter_mapping, scope_field, enforced) VALUES
('APC001', 'oa:attendance:query-late', '{"departmentId": "departmentId"}', 'departmentId', 1),
('APC002', 'oa:attendance:query-statistics', '{}', NULL, 1),
('APC003', 'oa:task:query-progress', '{"departmentId": "departmentId"}', 'departmentId', 1),
('APC004', 'gov:stats:query-appointment', '{"bureauId": "bureauId"}', 'bureauId', 1);
```

---

## 6. 适配器实现示例

### 6.1 OA系统适配器

```java
@Component
public class OaPermissionAdapter implements PermissionAdapter {

    @Override
    public String getSystemId() {
        return "oa-system";
    }

    @Override
    public StandardPermission adapt(Map<String, Object> rawContext) {
        String role = (String) rawContext.get("role");
        String deptId = (String) rawContext.get("deptId");
        String userId = (String) rawContext.get("userId");

        StandardPermission permission = new StandardPermission();
        permission.setUserId(userId);
        permission.setSystemId("oa-system");

        switch (role) {
            case "employee":
                // 员工：只能操作自己的数据
                permission.setAllowedActions(Arrays.asList(
                    "oa:attendance:apply-leave",
                    "oa:task:update-status"
                ));
                permission.setDataScope(DataScopeType.SELF);
                permission.addFilter("userId", "eq", userId);
                break;

            case "manager":
                // 经理：可以查看本部门数据
                permission.setAllowedActions(Arrays.asList(
                    "oa:attendance:apply-leave",
                    "oa:attendance:query-late",
                    "oa:task:update-status",
                    "oa:task:query-progress"
                ));
                permission.setDataScope(DataScopeType.DEPARTMENT);
                permission.addFilter("departmentId", "eq", deptId);
                break;

            case "director":
                // 大领导：可以查看全公司数据
                permission.setAllowedActions(Arrays.asList(
                    "oa:attendance:query-late",
                    "oa:attendance:query-statistics",
                    "oa:task:query-progress",
                    "oa:task:query-statistics"
                ));
                permission.setDataScope(DataScopeType.ORGANIZATION);
                // 无额外过滤条件
                break;

            default:
                // 默认无权限
                permission.setAllowedActions(Collections.emptyList());
                permission.setDataScope(DataScopeType.SELF);
        }

        return permission;
    }
}
```

### 6.2 政务平台适配器

```java
@Component
public class GovPermissionAdapter implements PermissionAdapter {

    @Override
    public String getSystemId() {
        return "gov-platform";
    }

    @Override
    public StandardPermission adapt(Map<String, Object> rawContext) {
        String userType = (String) rawContext.get("userType");
        String userId = (String) rawContext.get("userId");
        String bureauId = (String) rawContext.get("bureauId");

        StandardPermission permission = new StandardPermission();
        permission.setUserId(userId);
        permission.setSystemId("gov-platform");

        switch (userType) {
            case "citizen":
                // 群众：只能操作自己的数据
                permission.setAllowedActions(Arrays.asList(
                    "gov:service:query-process",
                    "gov:appointment:create"
                ));
                permission.setDataScope(DataScopeType.SELF);
                permission.addFilter("citizenId", "eq", userId);
                break;

            case "staff":
                // 业务处理人员：可以操作本业务板块
                permission.setAllowedActions(Arrays.asList(
                    "gov:appointment:set-limit",
                    "gov:content:publish-article"
                ));
                permission.setDataScope(DataScopeType.DEPARTMENT);
                permission.addFilter("bureauId", "eq", bureauId);
                break;

            case "leader":
                // 分管领导：可以查看分管板块统计
                permission.setAllowedActions(Arrays.asList(
                    "gov:stats:query-appointment",
                    "gov:stats:query-satisfaction"
                ));
                permission.setDataScope(DataScopeType.DEPARTMENT_TREE);
                permission.addFilter("bureauId", "eq", bureauId);
                break;

            default:
                permission.setAllowedActions(Collections.emptyList());
                permission.setDataScope(DataScopeType.SELF);
        }

        return permission;
    }
}
```

---

## 7. API设计

### 7.1 聊天接口（带权限）

```
POST /api/v1/chat
Headers:
  Authorization: Bearer {platform-jwt-token}
Body:
{
  "message": "查询今天谁迟到了",
  "targetSystem": "oa-system"  // 可选，可自动识别
}

Response:
{
  "success": true,
  "data": {
    "reply": "技术部今天有2人迟到：王五 09:15、赵六 09:32",
    "action": "oa:attendance:query-late",
    "systemId": "oa-system",
    "identity": {
      "externalUserId": "li.si@company.com",
      "role": "manager",
      "dataScope": "DEPARTMENT"
    }
  }
}
```

### 7.2 获取可访问系统

```
GET /api/v1/user/accessible-systems
Headers:
  Authorization: Bearer {platform-jwt-token}

Response:
{
  "success": true,
  "data": [
    {
      "systemId": "oa-system",
      "systemName": "OA办公系统",
      "externalUserId": "li.si@company.com",
      "externalUsername": "李四",
      "bound": true
    },
    {
      "systemId": "gov-platform",
      "systemName": "政务服务平台",
      "bound": false
    }
  ]
}
```

### 7.3 绑定外部系统

```
POST /api/v1/user/bind-system
Headers:
  Authorization: Bearer {platform-jwt-token}
Body:
{
  "systemId": "gov-platform",
  "externalUserId": "320102199001011234",
  "externalUsername": "李四",
  "extraInfo": {
    "userType": "citizen"
  }
}

Response:
{
  "success": true,
  "message": "绑定成功"
}
```

---

## 8. 实现任务清单

### Task 1: 创建权限数据模型

**Files:**
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/StandardPermission.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/DataScopeType.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/DataFilter.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/ExternalIdentity.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/AccessibleSystem.java`

### Task 2: 创建用户和映射模型

**Files:**
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/PlatformUser.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/UserIdentityMapping.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/model/ExternalSystemConfig.java`

### Task 3: 扩展ActionDefinition

**Files:**
- Modify: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/ActionDefinition.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/DataPermissionConfig.java`

### Task 4: 创建数据库表和初始化脚本

**Files:**
- Create: `assistant-agent-planning/docs/sql/permission-tables.sql`
- Create: `assistant-agent-planning/docs/sql/permission-init-data.sql`

### Task 5: 创建PermissionAdapter接口

**Files:**
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/spi/PermissionAdapter.java`
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/spi/PermissionCheckResult.java`

### Task 6: 实现OA权限适配器

**Files:**
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/permission/adapter/OaPermissionAdapter.java`
- Create: `assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/permission/adapter/OaPermissionAdapterTest.java`

### Task 7: 实现政务平台权限适配器

**Files:**
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/permission/adapter/GovPermissionAdapter.java`
- Create: `assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/permission/adapter/GovPermissionAdapterTest.java`

### Task 8: 创建身份映射服务

**Files:**
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/spi/IdentityMappingService.java`
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/permission/service/DefaultIdentityMappingService.java`
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/permission/repository/UserIdentityMappingRepository.java`

### Task 9: 创建权限服务

**Files:**
- Create: `assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/permission/spi/PermissionService.java`
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/permission/service/DefaultPermissionService.java`
- Create: `assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/permission/service/PermissionServiceTest.java`

### Task 10: 创建适配器注册中心

**Files:**
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/permission/adapter/PermissionAdapterRegistry.java`

### Task 11: 创建统一聊天服务

**Files:**
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/service/UnifiedChatService.java`
- Modify: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/service/ActionExecutionService.java`

### Task 12: 创建权限相关Controller

**Files:**
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/web/controller/PermissionController.java`
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/web/controller/UserBindingController.java`

### Task 13: 添加Spring Boot自动配置

**Files:**
- Create: `assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/config/PermissionAutoConfiguration.java`
- Modify: `assistant-agent-planning-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Task 14: 编写集成测试

**Files:**
- Create: `assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/permission/integration/PermissionIntegrationTest.java`
- Create: `assistant-agent-planning-core/src/test/java/com/alibaba/assistant/agent/planning/permission/integration/MultiSystemDemoTest.java`

---

## 9. 配置示例

### application.yml

```yaml
spring.ai.alibaba.codeact.extension.planning:
  enabled: true
  permission:
    enabled: true
    # 权限缓存配置
    cache:
      enabled: true
      ttl-minutes: 10
    # 默认数据范围
    default-data-scope: SELF
    # 是否强制权限校验
    enforce-permission-check: true
```

---

## 10. 总结

本设计实现了一套完整的多系统集成与权限体系：

1. **三层架构**：平台用户层 → 身份映射层 → 权限适配层
2. **统一权限模型**：StandardPermission 作为平台标准，适配器负责转换
3. **灵活扩展**：新增系统只需实现 PermissionAdapter
4. **数据权限注入**：自动将权限过滤条件注入到查询参数
5. **Demo友好**：提供完整的初始化数据和测试用例

通过此设计，用户可以：
- 登录平台一次，操作多个已绑定的外部系统
- 每个系统使用独立的身份和权限
- 数据权限自动过滤，确保用户只能看到有权限的数据
