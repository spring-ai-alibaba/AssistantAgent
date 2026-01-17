# Assistant Agent Planning 模块重新设计方案

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档名称 | Planning 模块重新设计方案 |
| 版本 | v1.0 |
| 创建日期 | 2026-01-16 |
| 作者 | Assistant Agent Team |
| 状态 | 设计中 |

---

## 目录

- [1. 背景与目标](#1-背景与目标)
- [2. 现状分析](#2-现状分析)
- [3. 问题识别](#3-问题识别)
- [4. 重新设计](#4-重新设计)
- [5. 模块结构](#5-模块结构)
- [6. SPI 接口设计](#6-spi-接口设计)
- [7. 数据模型](#7-数据模型)
- [8. 实现细节](#8-实现细节)
- [9. 配置设计](#9-配置设计)
- [10. 数据库设计](#10-数据库设计)
- [11. 兼容性与迁移](#11-兼容性与迁移)
- [12. 测试策略](#12-测试策略)
- [13. 实施计划](#13-实施计划)

---

## 1. 背景与目标

### 1.1 背景

assistant-agent-planning 模块已实现以下功能：
- Action 匹配（向量搜索 + 关键词）
- 参数收集和验证流程
- HTTP API 执行
- 集成到评估系统

### 1.2 新需求

基于企业 AI 助手平台的需求，planning 模块需要支持：
1. **多租户隔离** - 租户/系统/模块三级隔离
2. **权限管理** - RBAC 权限验证 + 数据权限
3. **会话持久化** - 支持分布式部署
4. **DataAgent 集成** - 作为 MCP Tool 集成
5. **模块解耦** - 与 evaluation 模块解耦

### 1.3 设计目标

| 目标 | 描述 |
|------|------|
| **可扩展性** | 通过 SPI 支持自定义扩展 |
| **可隔离性** | 支持多租户数据隔离 |
| **可持久化** | 会话状态持久化到 Redis/DB |
| **可集成性** | 支持 DataAgent 等外部服务 |
| **向后兼容** | 不破坏现有 API 和配置 |

---

## 2. 现状分析

### 2.1 模块结构

```
assistant-agent-planning/
├── assistant-agent-planning-api/       # API 模块
│   ├── model/                          # 数据模型
│   │   ├── ActionDefinition.java
│   │   ├── ActionMatch.java
│   │   ├── ActionParameter.java
│   │   ├── StepDefinition.java
│   │   └── ...
│   └── spi/                            # SPI 接口
│       ├── ActionProvider.java
│       ├── ActionRepository.java
│       └── ...
└── assistant-agent-planning-core/      # Core 模块
    ├── evaluation/
    │   └── ActionIntentEvaluator.java  # 与 evaluation 耦合
    ├── internal/
    │   └── SemanticActionProvider.java
    ├── param/
    │   ├── ParamCollectionSession.java
    │   ├── ParameterValidator.java
    │   └── StructuredParamExtractor.java
    ├── service/
    │   └── ParamCollectionService.java  # 内存存储会话
    ├── executor/
    │   └── ActionExecutor.java          # MCP 未实现
    └── config/
        └── ParamCollectionAutoConfiguration.java
```

### 2.2 核心组件统计

| 组件 | 类 | 行数 | 状态 |
|------|-----|------|------|
| ActionDefinition | 模型 | 158 | ✅ 需扩展租户字段 |
| SemanticActionProvider | 匹配 | 314 | ✅ 需添加租户过滤 |
| ActionIntentEvaluator | 评估 | 278 | ⚠️ 与 evaluation 耦合 |
| ParamCollectionService | 收集 | 535 | ⚠️ 内存存储 |
| ParameterValidator | 验证 | 417 | ⚠️ 编译错误 |
| StructuredParamExtractor | 提取 | ~340 | ✅ 正常 |
| ActionExecutor | 执行 | 349 | ⚠️ MCP 未实现 |
| **总计** | | **~2400** | |

### 2.3 数据库 Schema

```sql
-- action_registry 表（现有）
CREATE TABLE action_registry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    action_id VARCHAR(100) NOT NULL,
    action_name VARCHAR(200) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    keywords JSON,
    synonyms JSON,
    parameters JSON,
    interface_binding JSON,
    enabled TINYINT(1) DEFAULT 1,
    ...
);
```

**问题**：缺少租户相关字段

---

## 3. 问题识别

### 3.1 架构问题

#### 问题 1: 与评估系统紧耦合

**现状**:
```java
// ActionIntentEvaluator 实现了 assistant-agent-evaluation 的 Evaluator 接口
public class ActionIntentEvaluator implements Evaluator {
    @Override
    public CriterionResult evaluate(CriterionExecutionContext context) {
        // planning 模块依赖 evaluation 模块
    }
}
```

**影响**:
- planning 模块无法独立使用
- 强依赖 evaluation 模块的类和接口
- 违反模块独立性原则

#### 问题 2: 缺少租户感知

**现状**:
```java
// ActionDefinition 没有租户字段
public class ActionDefinition {
    private String actionId;
    private String actionName;
    // ❌ 缺少 tenantId, systemId, moduleId
}

// SemanticActionProvider 没有租户过滤
public List<ActionMatch> matchActions(String userInput, Map<String, Object> context) {
    // ❌ 返回所有租户的 actions
}
```

**影响**:
- 无法支持多租户隔离
- 所有租户共享同一 action 注册表
- 数据安全问题

#### 问题 3: 会话存储不可靠

**现状**:
```java
public class ParamCollectionService {
    // ❌ 内存存储，重启丢失
    private final Map<String, ParamCollectionSession> sessions = new ConcurrentHashMap<>();
}
```

**影响**:
- 服务重启丢失会话状态
- 无法跨实例扩展
- 用户体验差

#### 问题 4: 参数收集流程耦合

**现状**:
```java
// ActionIntentEvaluator 直接管理参数收集会话
private CriterionResult handleParamCollection(...) {
    ParamCollectionSession session = paramCollectionService.createSession(...);
    ProcessResult result = paramCollectionService.processUserInput(...);
    // 职责不清晰，难以扩展
}
```

**影响**:
- 难以定制流程
- 无法扩展其他收集策略
- 代码可测试性差

### 3.2 功能缺失

#### 缺失 1: 权限管理

**现状**:
- `ActionDefinition` 有 `requiredPermissions` 字段但未使用
- 没有权限检查逻辑
- 没有数据权限过滤

**需求**:
- 执行前检查用户权限
- 支持数据权限范围过滤
- 集成企业 RBAC 系统

#### 缺失 2: MCP 执行器

**现状**:
```java
public static class McpExecutor {
    public ExecutionResult execute(...) {
        // ❌ TODO: 未实现
        return ExecutionResult.builder()
                .success(false)
                .errorMessage("MCP 执行器尚未实现")
                .build();
    }
}
```

**需求**:
- 完整实现 MCP Tool 调用
- 支持 DataAgent 作为 MCP Server
- 支持多 MCP Server

#### 缺失 3: 上下文管理

**现状**:
- 没有统一的上下文管理机制
- 租户/系统/用户信息传递依赖参数

**需求**:
- ThreadLocal 上下文存储
- 自动传递租户信息
- 支持上下文清理

### 3.3 代码问题

#### 问题 1: 编译错误

**文件**: `ParameterValidator.java:26`

```java
import time.LocalDateTime;  // ❌ 错误的导入
```

**应为**:
```java
import java.time.LocalDateTime;
```

#### 问题 2: 内部类设计

**文件**: `ActionExecutor.java`

```java
// McpExecutor 和 InternalExecutor 作为内部类
public static class McpExecutor { ... }
public static class InternalExecutor { ... }
```

**问题**:
- 无法单独扩展
- 无法注入依赖
- 不符合 Spring Bean 设计

---

## 4. 重新设计

### 4.1 设计原则

| 原则 | 说明 |
|------|------|
| **SPI 优先** | 所有扩展点通过 SPI 接口实现 |
| **模块独立** | api/core/integration 三层结构，模块间低耦合 |
| **租户感知** | 所有组件支持多租户隔离 |
| **可持久化** | 会话状态支持多种存储后端 |
| **向后兼容** | 不破坏现有 API |

### 4.2 设计策略

#### 策略 1: 模块重组

```
原结构:
assistant-agent-planning/
├── assistant-agent-planning-api/
└── assistant-agent-planning-core/      # 包含 evaluation 集成

新结构:
assistant-agent-planning/
├── assistant-agent-planning-api/       # 纯 API
├── assistant-agent-planning-core/      # 核心实现，无外部依赖
└── assistant-agent-planning-integration/  # 集成层（evaluation, agent 等）
```

#### 策略 2: SPI 扩展点

| SPI | 职责 | 实现示例 |
|-----|------|----------|
| `SessionProvider` | 会话存储 | InMemorySessionProvider, RedisSessionProvider |
| `PermissionProvider` | 权限验证 | RbacPermissionProvider |
| `ExecutionContextProvider` | 上下文提供 | ThreadLocalContextHolder |
| `ActionExecutor` | Action 执行 | HttpActionExecutor, McpActionExecutor |

#### 策略 3: 租户集成

1. **数据模型层**: `ActionDefinition` 添加租户字段
2. **匹配层**: `SemanticActionProvider` 添加租户过滤
3. **上下文层**: `TenantContext` ThreadLocal 存储
4. **权限层**: `PermissionProvider` 集成权限检查

---

## 5. 模块结构

### 5.1 新模块结构

```
assistant-agent-planning/
│
├── assistant-agent-planning-api/           # API 模块
│   ├── model/                              # 数据模型
│   │   ├── ActionDefinition.java          # 扩展租户字段
│   │   ├── ActionMatch.java
│   │   ├── ActionParameter.java
│   │   ├── ParameterSource.java
│   │   ├── StepDefinition.java
│   │   └── ...
│   │
│   ├── spi/                                # SPI 接口
│   │   ├── action/                         # Action 相关 SPI
│   │   │   ├── ActionProvider.java
│   │   │   ├── ActionRepository.java
│   │   │   └── ActionMatcher.java
│   │   │
│   │   ├── session/                        # 会话 SPI
│   │   │   ├── SessionProvider.java
│   │   │   └── SessionManager.java
│   │   │
│   │   ├── permission/                     # 权限 SPI
│   │   │   ├── PermissionProvider.java
│   │   │   └── DataScopeProvider.java
│   │   │
│   │   ├── context/                        # 上下文 SPI
│   │   │   └── ExecutionContextProvider.java
│   │   │
│   │   └── executor/                       # 执行器 SPI
│   │       ├── ActionExecutor.java
│   │       └── ExecutorFactory.java
│   │
│   └── collection/                         # 参数收集 API
│       ├── ParamCollectionSession.java
│       ├── CollectionResult.java
│       ├── CollectionState.java
│       └── CollectionStrategy.java
│
├── assistant-agent-planning-core/           # Core 模块
│   ├── matching/                           # 匹配引擎
│   │   ├── SemanticActionProvider.java     # 添加租户过滤
│   │   ├── KeywordMatcher.java
│   │   ├── VectorMatcher.java
│   │   └── TenantAwareActionProvider.java  # NEW: 租户感知包装器
│   │
│   ├── collection/                         # 参数收集
│   │   ├── DefaultCollectionStrategy.java  # NEW: 默认策略
│   │   ├── StructuredParamExtractor.java
│   │   └── ParameterValidator.java         # 修复编译错误
│   │
│   ├── execution/                          # 执行引擎
│   │   ├── ActionExecutorFactory.java      # NEW: 工厂模式
│   │   ├── HttpActionExecutor.java         # NEW
│   │   ├── McpActionExecutor.java          # NEW: 完整实现
│   │   ├── InternalActionExecutor.java     # NEW: 完整实现
│   │   ├── DataAgentToolExecutor.java      # NEW: DataAgent 执行器
│   │   └── mcp/                            # MCP 支持
│   │       ├── McpClientManager.java
│   │       └── McpToolInvoker.java
│   │
│   ├── session/                            # 会话管理
│   │   ├── SessionManager.java             # NEW: 会话管理器
│   │   ├── InMemorySessionProvider.java    # 内存实现
│   │   └── RedisSessionProvider.java       # NEW: Redis 实现
│   │
│   ├── permission/                         # NEW: 权限管理
│   │   ├── RbacPermissionProvider.java
│   │   ├── DataScopeFilter.java
│   │   └── PermissionChecker.java
│   │
│   ├── context/                            # NEW: 上下文管理
│   │   ├── TenantContext.java
│   │   ├── ThreadLocalContextHolder.java
│   │   └── RequestContextInterceptor.java  # Web 拦截器
│   │
│   ├── repository/                         # 数据访问
│   │   ├── MybatisPlusActionRepository.java
│   │   └── ActionRegistryMapper.java
│   │
│   ├── vector/                             # 向量搜索
│   │   ├── ActionVectorizationService.java
│   │   └── ActionVectorDocument.java
│   │
│   ├── web/                                # Web API（可选）
│   │   ├── controller/
│   │   └── dto/
│   │
│   └── config/                             # 配置
│       ├── PlanningExtensionAutoConfiguration.java
│       ├── PlanningExtensionProperties.java
│       ├── ParamCollectionAutoConfiguration.java
│       └── McpAutoConfiguration.java       # NEW
│
└── assistant-agent-planning-integration/    # NEW: 集成模块
    ├── evaluation/                         # 与 evaluation 模块集成
    │   └── ActionIntentEvaluator.java      # 从 core 移动过来
    │
    ├── agent/                              # 与 CodeactAgent 集成
    │   ├── PlanningEvaluationHook.java
    │   ├── PlanningPromptBuilder.java
    │   └── PlanningIntentHook.java
    │
    └── config/
        └── EvaluationIntegrationAutoConfiguration.java
```

### 5.2 模块依赖关系

```
┌─────────────────────────────────────────────────────────────┐
│                  assistant-agent-planning-integration        │
│                    (evaluation, agent 集成)                  │
└────────────────────────┬────────────────────────────────────┘
                         │ depends on
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   assistant-agent-planning-core              │
│                    (核心实现，无外部依赖)                     │
└────────────────────────┬────────────────────────────────────┘
                         │ depends on
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  assistant-agent-planning-api                │
│                        (API + SPI)                           │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 与其他模块的关系

```
┌─────────────────────────────────────────────────────────────┐
│                    assistant-agent-evaluation                │
│                           ▲                                  │
│                           │ integrates with                  │
┌──────────────────────────┼──────────────────────────────────┐
│                          │                                  │
│   assistant-agent-planning-integration                      │
│   (ActionIntentEvaluator implements Evaluator)              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                       DataAgent                              │
│                           ▲                                  │
│                           │ connects via                     │
┌──────────────────────────┼──────────────────────────────────┐
│                          │                                  │
│   assistant-agent-planning-core                             │
│   (McpActionExecutor -> DataAgent MCP Server)               │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. SPI 接口设计

### 6.1 SessionProvider SPI

```java
package com.alibaba.assistant.agent.planning.spi.session;

import com.alibaba.assistant.agent.planning.collection.ParamCollectionSession;

/**
 * 参数收集会话存储提供者 SPI
 *
 * <p>支持多种存储实现：
 * <ul>
 * <li>InMemorySessionProvider - 内存存储（开发/测试）</li>
 * <li>RedisSessionProvider - Redis 存储（生产环境）</li>
 * <li>DatabaseSessionProvider - 数据库存储（持久化）</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
public interface SessionProvider {

    /**
     * 保存会话
     *
     * @param session 会话对象
     */
    void saveSession(ParamCollectionSession session);

    /**
     * 获取会话
     *
     * @param sessionId 会话 ID
     * @return 会话对象，不存在返回 null
     */
    ParamCollectionSession getSession(String sessionId);

    /**
     * 根据 Assistant Session ID 获取活跃的参数收集会话
     *
     * @param assistantSessionId Assistant 会话 ID
     * @return 活跃的会话对象，不存在返回 null
     */
    ParamCollectionSession getActiveSessionByAssistantSessionId(String assistantSessionId);

    /**
     * 删除会话
     *
     * @param sessionId 会话 ID
     */
    void deleteSession(String sessionId);

    /**
     * 清理过期会话
     *
     * @return 清理的会话数量
     */
    int cleanupExpiredSessions();

    /**
     * 获取提供者名称
     *
     * @return 提供者名称
     */
    String getProviderName();

    /**
     * 获取提供者优先级（数字越大优先级越高）
     *
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }
}
```

**实现示例**:

```java
// Redis 实现
@Component
@ConditionalOnProperty(name = "spring.ai.alibaba.codeact.extension.planning.session.provider-type",
                       havingValue = "redis")
public class RedisSessionProvider implements SessionProvider {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix = "param:session:";

    @Override
    public void saveSession(ParamCollectionSession session) {
        String key = keyPrefix + session.getSessionId();
        redisTemplate.opsForValue().set(key, session, 1, TimeUnit.HOURS);
    }

    @Override
    public ParamCollectionSession getSession(String sessionId) {
        String key = keyPrefix + sessionId;
        return (ParamCollectionSession) redisTemplate.opsForValue().get(key);
    }

    // ... 其他方法
}
```

### 6.2 PermissionProvider SPI

```java
package com.alibaba.assistant.agent.planning.spi.permission;

import java.util.List;

/**
 * 权限验证提供者 SPI
 *
 * <p>用于验证用户是否有权执行特定 action，以及获取数据权限范围。
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
public interface PermissionProvider {

    /**
     * 检查用户是否有权限执行 action
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @param userId 用户 ID
     * @param actionId Action ID
     * @param requiredRoles Action 要求的角色列表
     * @return 权限检查结果
     */
    PermissionCheckResult checkPermission(
        Long tenantId,
        Long systemId,
        Long userId,
        String actionId,
        List<String> requiredRoles
    );

    /**
     * 获取用户的数据权限范围
     *
     * @param userId 用户 ID
     * @param resourceType 资源类型
     * @return 数据权限范围
     */
    DataScope getDataScope(Long userId, String resourceType);

    /**
     * 权限检查结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class PermissionCheckResult {
        /**
         * 是否允许
         */
        private boolean allowed;

        /**
         * 原因（不允许时）
         */
        private String reason;

        /**
         * 缺失的角色
         */
        private List<String> missingRoles;
    }

    /**
     * 数据权限范围
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DataScope {
        /**
         * 范围类型: ALL, SELF, DEPT, ORG, CUSTOM
         */
        private String type;

        /**
         * SQL WHERE 表达式（用于数据过滤）
         */
        private String expression;

        /**
         * 范围 ID 列表（如部门 ID 列表）
         */
        private List<Long> scopeIds;
    }
}
```

**实现示例**:

```java
// RBAC 实现
@Component
public class RbacPermissionProvider implements PermissionProvider {

    private final UserRoleService userRoleService;
    private final RolePermissionService rolePermissionService;

    @Override
    public PermissionCheckResult checkPermission(Long tenantId, Long systemId,
                                                  Long userId, String actionId,
                                                  List<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return PermissionCheckResult.builder().allowed(true).build();
        }

        // 获取用户角色
        List<String> userRoles = userRoleService.getUserRoles(tenantId, systemId, userId);

        // 检查是否有匹配的角色
        boolean hasRole = requiredRoles.stream().anyMatch(userRoles::contains);

        if (!hasRole) {
            return PermissionCheckResult.builder()
                    .allowed(false)
                    .reason("缺少必需的角色")
                    .missingRoles(requiredRoles)
                    .build();
        }

        return PermissionCheckResult.builder().allowed(true).build();
    }

    // ...
}
```

### 6.3 ActionExecutor SPI

```java
package com.alibaba.assistant.agent.planning.spi.executor;

import com.alibaba.assistant.agent.planning.model.StepDefinition;
import java.util.Map;

/**
 * Action 执行器 SPI
 *
 * <p>支持多种执行类型：
 * <ul>
 * <li>HTTP - HTTP API 调用</li>
 * <li>MCP - MCP Tool 调用</li>
 * <li>INTERNAL - 内部服务调用</li>
 * <li>DATAAGENT - DataAgent 查询</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
public interface ActionExecutor {

    /**
     * 执行 action
     *
     * @param binding 接口绑定配置
     * @param params 参数值
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果
     */
    ExecutionResult execute(
        StepDefinition.InterfaceBinding binding,
        Map<String, Object> params,
        Integer timeoutSeconds
    );

    /**
     * 获取支持的执行类型
     *
     * @return 执行类型（HTTP, MCP, INTERNAL, DATAAGENT 等）
     */
    String getSupportedType();

    /**
     * 执行结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class ExecutionResult {
        /**
         * 是否成功
         */
        private boolean success;

        /**
         * HTTP 状态码（如果是 HTTP 接口）
         */
        private Integer statusCode;

        /**
         * 响应体（已解析为对象）
         */
        private Object responseBody;

        /**
         * 原始响应（字符串）
         */
        private String rawResponse;

        /**
         * 响应头
         */
        private Map<String, String> headers;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 执行时长（毫秒）
         */
        private Long durationMs;
    }
}
```

---

## 7. 数据模型

### 7.1 ActionDefinition 扩展

```java
package com.alibaba.assistant.agent.planning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 动作定义
 *
 * <p>完整的动作定义，包括基本信息、参数、步骤配置、租户信息等。
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDefinition {

    // ========== 现有字段 ==========

    /**
     * 动作唯一标识
     */
    private String actionId;

    /**
     * 动作名称
     */
    private String actionName;

    /**
     * 动作描述
     */
    private String description;

    /**
     * 动作类型
     */
    private ActionType actionType;

    /**
     * 动作分类
     */
    private String category;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 触发关键词
     */
    private List<String> triggerKeywords;

    /**
     * 同义词列表
     */
    private List<String> synonyms;

    /**
     * 示例输入（用于训练和匹配）
     */
    private List<String> exampleInputs;

    /**
     * 参数定义列表
     */
    private List<ActionParameter> parameters;

    /**
     * 步骤定义列表（多步骤动作）
     */
    private List<StepDefinition> steps;

    /**
     * 状态 Schema 定义（用于多步骤状态管理）
     */
    private Map<String, Object> stateSchema;

    /**
     * 处理器类名（单步骤动作）
     */
    private String handler;

    /**
     * 接口绑定配置（单步骤动作）
     */
    private StepDefinition.InterfaceBinding interfaceBinding;

    /**
     * 优先级
     */
    @Builder.Default
    private Integer priority = 0;

    /**
     * 默认超时时间（分钟）
     */
    @Builder.Default
    private Integer timeoutMinutes = 30;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 权限要求
     */
    private List<String> requiredPermissions;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    // ========== NEW: 租户相关字段 ==========

    /**
     * 租户 ID
     * <p>null 表示全局 action，所有租户可用
     */
    private Long tenantId;

    /**
     * 系统 ID
     * <p>null 表示租户级 action，租户内所有系统可用
     */
    private Long systemId;

    /**
     * 模块 ID
     * <p>null 表示系统级 action，系统内所有模块可用
     */
    private Long moduleId;

    /**
     * 允许的角色列表
     * <p>用户至少需要拥有其中一个角色才能执行此 action
     */
    private List<String> allowedRoles;

    /**
     * 数据权限配置
     */
    private DataPermissionConfig dataPermissionConfig;

    // ========== 辅助方法 ==========

    /**
     * 判断是否为多步骤动作
     */
    public boolean isMultiStep() {
        return ActionType.MULTI_STEP.equals(actionType)
                || (steps != null && !steps.isEmpty());
    }

    /**
     * 获取必填参数
     */
    public List<ActionParameter> getRequiredParameters() {
        if (parameters == null) {
            return List.of();
        }
        return parameters.stream()
                .filter(p -> Boolean.TRUE.equals(p.getRequired()))
                .toList();
    }

    /**
     * 判断是否属于指定租户和系统
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return true-属于当前租户/系统，false-不属于
     */
    public boolean belongsToTenant(Long tenantId, Long systemId) {
        // 全局 action
        if (this.tenantId == null) {
            return true;
        }

        // 租户不匹配
        if (!this.tenantId.equals(tenantId)) {
            return false;
        }

        // 系统 ID 为 null 或匹配
        return this.systemId == null || this.systemId.equals(systemId);
    }

    /**
     * 判断是否为租户全局 action
     */
    public boolean isGlobalAction() {
        return this.tenantId == null;
    }

    /**
     * 判断是否为租户级 action（但不是系统级）
     */
    public boolean isTenantLevelAction() {
        return this.tenantId != null && this.systemId == null;
    }

    /**
     * 数据权限配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPermissionConfig {
        /**
         * 资源类型（用于数据权限过滤）
         */
        private String resourceType;

        /**
         * 是否需要数据权限过滤
         */
        @Builder.Default
        private Boolean enableDataScope = false;

        /**
         * 数据权限字段（如 org_id, dept_id）
         */
        private String dataScopeField;

        /**
         * 权限范围类型
         */
        private String scopeType; // ALL, SELF, DEPT, ORG, CUSTOM
    }
}
```

### 7.2 ParamCollectionSession 扩展

```java
package com.alibaba.assistant.agent.planning.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 参数收集会话
 *
 * <p>扩展添加租户信息字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamCollectionSession {

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * Action ID
     */
    private String actionId;

    /**
     * Assistant Agent 会话 ID
     */
    private String assistantSessionId;

    /**
     * 用户 ID
     */
    private String userId;

    // ========== NEW: 租户信息 ==========

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 系统 ID
     */
    private Long systemId;

    // ========== 现有字段 ==========

    /**
     * 会话状态
     */
    @Builder.Default
    private CollectionState state = CollectionState.INIT;

    /**
     * 已收集的参数
     */
    @Builder.Default
    private Map<String, CollectedParam> collectedParams = new java.util.HashMap<>();

    /**
     * 缺失的参数列表
     */
    @Builder.Default
    private java.util.List<MissingParamInfo> missingParams = new java.util.ArrayList<>();

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new java.util.HashMap<>();

    // ========== 状态枚举 ==========

    public enum CollectionState {
        /**
         * 初始状态
         */
        INIT,

        /**
         * 收集中
         */
        COLLECTING,

        /**
         * 待确认
         */
        PENDING_CONFIRM,

        /**
         * 已确认
         */
        CONFIRMED,

        /**
         * 执行中
         */
        EXECUTING,

        /**
         * 已完成
         */
        COMPLETED,

        /**
         * 已取消
         */
        CANCELLED,

        /**
         * 已过期
         */
        EXPIRED,

        /**
         * 失败
         */
        FAILED
    }

    // ========== 辅助方法 ==========

    /**
     * 判断是否可以继续收集参数
     */
    public boolean canCollect() {
        return state == CollectionState.INIT || state == CollectionState.COLLECTING;
    }

    /**
     * 判断是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 获取参数值
     */
    public Object getParamValue(String paramName) {
        CollectedParam param = collectedParams.get(paramName);
        return param != null ? param.getValue() : null;
    }

    /**
     * 设置参数值
     */
    public void setParamValue(String name, Object value, String type,
                             double confidence, String source) {
        CollectedParam param = CollectedParam.builder()
                .name(name)
                .value(value)
                .type(type)
                .confidence(confidence)
                .source(source)
                .build();
        collectedParams.put(name, param);
    }

    /**
     * 更新为收集中状态
     */
    public void updateToCollecting() {
        this.state = CollectionState.COLLECTING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新为待确认状态
     */
    public void updateToPendingConfirm() {
        this.state = CollectionState.PENDING_CONFIRM;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 确认会话
     */
    public void confirm() {
        this.state = CollectionState.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为完成
     */
    public void markCompleted() {
        this.state = CollectionState.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为失败
     */
    public void markFailed(String errorMessage) {
        this.state = CollectionState.FAILED;
        this.metadata.put("errorMessage", errorMessage);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为取消
     */
    public void markCancelled() {
        this.state = CollectionState.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    // ========== 内部类 ==========

    /**
     * 已收集的参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectedParam {
        private String name;
        private Object value;
        private String type;
        private double confidence;
        private String source;
    }

    /**
     * 缺失参数信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingParamInfo {
        private String name;
        private String label;
        private String type;
        private Boolean required;
        private String description;
        private String promptHint;
        private java.util.List<String> enumOptions;
        private Object defaultValue;
    }
}
```

### 7.3 TenantContext 上下文

```java
package com.alibaba.assistant.agent.planning.context;

/**
 * 租户上下文
 *
 * <p>使用 ThreadLocal 存储当前请求的租户、系统、用户信息。
 * <p>在请求开始时设置，请求结束时清理。
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
public class TenantContext {

    private static final ThreadLocal<TenantInfo> CONTEXT = new ThreadLocal<>();

    /**
     * 设置租户上下文
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID（可选）
     * @param userId 用户 ID
     */
    public static void set(Long tenantId, Long systemId, Long userId) {
        CONTEXT.set(new TenantInfo(tenantId, systemId, userId));
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID，未设置返回 null
     */
    public static Long getTenantId() {
        TenantInfo info = CONTEXT.get();
        return info != null ? info.getTenantId() : null;
    }

    /**
     * 获取系统 ID
     *
     * @return 系统 ID，未设置返回 null
     */
    public static Long getSystemId() {
        TenantInfo info = CONTEXT.get();
        return info != null ? info.getSystemId() : null;
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID，未设置返回 null
     */
    public static Long getUserId() {
        TenantInfo info = CONTEXT.get();
        return info != null ? info.getUserId() : null;
    }

    /**
     * 获取完整的上下文信息
     *
     * @return 上下文信息，未设置返回 null
     */
    public static TenantInfo get() {
        return CONTEXT.get();
    }

    /**
     * 清除上下文
     * <p>应在请求结束时调用，防止内存泄漏
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 判断上下文是否已设置
     *
     * @return true-已设置，false-未设置
     */
    public static boolean isSet() {
        return CONTEXT.get() != null;
    }

    /**
     * 在指定上下文中执行代码
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @param userId 用户 ID
     * @param runnable 要执行的代码
     */
    public static void runWith(Long tenantId, Long systemId, Long userId, Runnable runnable) {
        try {
            set(tenantId, systemId, userId);
            runnable.run();
        } finally {
            clear();
        }
    }

    /**
     * 在指定上下文中执行代码并返回结果
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @param userId 用户 ID
     * @param supplier 要执行的代码
     * @param <T> 返回类型
     * @return 执行结果
     */
    public static <T> T supplyWith(Long tenantId, Long systemId, Long userId,
                                   java.util.function.Supplier<T> supplier) {
        try {
            set(tenantId, systemId, userId);
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * 租户信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.Builder
    @lombok.NoArgsConstructor
    public static class TenantInfo {
        /**
         * 租户 ID
         */
        private Long tenantId;

        /**
         * 系统 ID（可选）
         */
        private Long systemId;

        /**
         * 用户 ID
         */
        private Long userId;

        /**
         * 额外属性
         */
        @lombok.Builder.Default
        private java.util.Map<String, Object> attributes = new java.util.HashMap<>();
    }
}
```

---

## 8. 实现细节

### 8.1 租户感知的 ActionProvider

```java
package com.alibaba.assistant.agent.planning.matching;

import com.alibaba.assistant.agent.planning.context.TenantContext;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import com.alibaba.assistant.agent.planning.spi.action.ActionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 租户感知的 Action Provider
 *
 * <p>包装器模式，在原有 ActionProvider 基础上添加租户过滤功能。
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
public class TenantAwareActionProvider implements ActionProvider {

    private static final Logger logger = LoggerFactory.getLogger(TenantAwareActionProvider.class);

    private final ActionProvider delegate;

    public TenantAwareActionProvider(ActionProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<ActionDefinition> getAllActions() {
        List<ActionDefinition> allActions = delegate.getAllActions();

        // 过滤租户
        Long tenantId = TenantContext.getTenantId();
        Long systemId = TenantContext.getSystemId();

        if (tenantId == null) {
            // 未设置租户上下文，返回全局 actions
            return allActions.stream()
                    .filter(ActionDefinition::isGlobalAction)
                    .collect(Collectors.toList());
        }

        // 过滤属于当前租户/系统的 actions
        return allActions.stream()
                .filter(action -> action.belongsToTenant(tenantId, systemId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ActionMatch> matchActions(String userInput, Map<String, Object> context) {
        List<ActionMatch> allMatches = delegate.matchActions(userInput, context);

        // 过滤租户
        Long tenantId = TenantContext.getTenantId();
        Long systemId = TenantContext.getSystemId();

        if (tenantId == null) {
            // 未设置租户上下文，只返回全局 actions
            return allMatches.stream()
                    .filter(match -> match.getAction().isGlobalAction())
                    .collect(Collectors.toList());
        }

        // 过滤属于当前租户/系统的 actions
        return allMatches.stream()
                .filter(match -> match.getAction().belongsToTenant(tenantId, systemId))
                .collect(Collectors.toList());
    }

    @Override
    public String getProviderName() {
        return "TenantAwareActionProvider(" + delegate.getProviderName() + ")";
    }

    @Override
    public int getPriority() {
        return delegate.getPriority() + 10; // 提高优先级
    }

    // 其他方法直接委托
    @Override
    public java.util.Optional<ActionDefinition> getAction(String actionId) {
        return delegate.getAction(actionId);
    }

    @Override
    public java.util.Optional<ActionDefinition> getActionByName(String actionName) {
        return delegate.getActionByName(actionName);
    }

    @Override
    public List<ActionDefinition> getActionsByCategory(String category) {
        return delegate.getActionsByCategory(category)
                .stream()
                .filter(action -> {
                    Long tenantId = TenantContext.getTenantId();
                    Long systemId = TenantContext.getSystemId();
                    return tenantId == null || action.belongsToTenant(tenantId, systemId);
                })
                .collect(Collectors.toList());
    }

    // ... 其他委托方法
}
```

### 8.2 ActionExecutor 工厂模式

```java
package com.alibaba.assistant.agent.planning.execution;

import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.spi.executor.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Action 执行器工厂
 *
 * <p>管理多种类型的执行器，根据接口绑定配置路由到对应的执行器。
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
@Component
public class ActionExecutorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ActionExecutorFactory.class);

    private final Map<String, ActionExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 构造函数，自动注册所有 ActionExecutor Bean
     */
    public ActionExecutorFactory(java.util.List<ActionExecutor> executorList) {
        for (ActionExecutor executor : executorList) {
            registerExecutor(executor);
        }
        logger.info("ActionExecutorFactory#init - registered {} executors: {}",
                executors.size(), executors.keySet());
    }

    /**
     * 注册执行器
     */
    public void registerExecutor(ActionExecutor executor) {
        String type = executor.getSupportedType();
        if (type == null || type.isBlank()) {
            logger.warn("ActionExecutorFactory#registerExecutor - executor type is null, skipping");
            return;
        }
        executors.put(type.toUpperCase(), executor);
        logger.info("ActionExecutorFactory#registerExecutor - registered executor for type: {}", type);
    }

    /**
     * 执行 action
     *
     * @param binding 接口绑定配置
     * @param params 参数值
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果
     */
    public ActionExecutor.ExecutionResult execute(
            StepDefinition.InterfaceBinding binding,
            Map<String, Object> params,
            Integer timeoutSeconds) {

        if (binding == null) {
            return ActionExecutor.ExecutionResult.builder()
                    .success(false)
                    .errorMessage("接口绑定配置为空")
                    .build();
        }

        String type = binding.getType();
        if (type == null || type.isBlank()) {
            return ActionExecutor.ExecutionResult.builder()
                    .success(false)
                    .errorMessage("接口类型未配置")
                    .build();
        }

        ActionExecutor executor = executors.get(type.toUpperCase());
        if (executor == null) {
            logger.error("ActionExecutorFactory#execute - unsupported executor type: {}", type);
            return ActionExecutor.ExecutionResult.builder()
                    .success(false)
                    .errorMessage("不支持的执行器类型：" + type)
                    .build();
        }

        logger.info("ActionExecutorFactory#execute - executing with {} executor, type={}",
                executor.getClass().getSimpleName(), type);

        return executor.execute(binding, params, timeoutSeconds);
    }

    /**
     * 获取所有已注册的执行器类型
     */
    public java.util.Set<String> getSupportedTypes() {
        return executors.keySet();
    }
}
```

### 8.3 MCP 执行器实现

```java
package com.alibaba.assistant.agent.planning.execution;

import com.alibaba.assistant.agent.planning.execution.mcp.McpClientManager;
import com.alibaba.assistant.agent.planning.execution.mcp.McpToolCallRequest;
import com.alibaba.assistant.agent.planning.execution.mcp.McpToolCallResponse;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.spi.executor.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 工具执行器
 *
 * <p>通过 MCP 协议调用外部工具，支持 DataAgent 等 MCP Server。
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
@Component
public class McpActionExecutor implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(McpActionExecutor.class);

    private final McpClientManager mcpClientManager;

    public McpActionExecutor(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    @Override
    public ExecutionResult execute(StepDefinition.InterfaceBinding binding,
                                   Map<String, Object> params,
                                   Integer timeoutSeconds) {
        StepDefinition.McpConfig config = binding.getMcp();
        if (config == null) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("MCP 配置为空")
                    .build();
        }

        String serverName = config.getServerName();
        String toolName = config.getToolName();

        logger.info("McpActionExecutor#execute - server={}, tool={}", serverName, toolName);

        try {
            // 1. 获取 MCP 客户端
            McpClient client = mcpClientManager.getClient(serverName);
            if (client == null) {
                return ExecutionResult.builder()
                        .success(false)
                        .errorMessage("MCP Server 未找到: " + serverName)
                        .build();
            }

            // 2. 构建请求
            McpToolCallRequest request = McpToolCallRequest.builder()
                    .toolName(toolName)
                    .arguments(params)
                    .timeoutMs(timeoutSeconds != null ? timeoutSeconds * 1000L : 30000L)
                    .build();

            // 3. 调用工具
            long startTime = System.currentTimeMillis();
            McpToolCallResponse response = client.callTool(request);
            long duration = System.currentTimeMillis() - startTime;

            if (!response.isSuccess()) {
                logger.error("McpActionExecutor#execute - tool call failed, error={}",
                        response.getError());
                return ExecutionResult.builder()
                        .success(false)
                        .errorMessage(response.getError())
                        .durationMs(duration)
                        .build();
            }

            logger.info("McpActionExecutor#execute - completed successfully, duration={}ms", duration);

            return ExecutionResult.builder()
                    .success(true)
                    .responseBody(response.getResult())
                    .rawResponse(response.getRawResult())
                    .durationMs(duration)
                    .build();

        } catch (Exception e) {
            logger.error("McpActionExecutor#execute - execution failed", e);
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("MCP 执行失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String getSupportedType() {
        return "MCP";
    }
}
```

### 8.4 DataAgent 执行器

```java
package com.alibaba.assistant.agent.planning.execution;

import com.alibaba.assistant.agent.planning.context.TenantContext;
import com.alibaba.assistant.agent.planning.execution.datagent.DataAgentMcpClient;
import com.alibaba.assistant.agent.planning.execution.datagent.DataAgentQueryRequest;
import com.alibaba.assistant.agent.planning.execution.datagent.DataAgentQueryResponse;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.spi.executor.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DataAgent 工具执行器
 *
 * <p>通过 MCP 协议调用 DataAgent 服务进行数据查询。
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
@Component
public class DataAgentToolExecutor implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DataAgentToolExecutor.class);

    private final DataAgentMcpClient dataAgentClient;

    public DataAgentToolExecutor(DataAgentMcpClient dataAgentClient) {
        this.dataAgentClient = dataAgentClient;
    }

    @Override
    public ExecutionResult execute(StepDefinition.InterfaceBinding binding,
                                   Map<String, Object> params,
                                   Integer timeoutSeconds) {

        // 获取当前租户上下文
        Long tenantId = TenantContext.getTenantId();
        Long systemId = TenantContext.getSystemId();
        Long userId = TenantContext.getUserId();

        logger.info("DataAgentToolExecutor#execute - tenantId={}, systemId={}, userId={}",
                tenantId, systemId, userId);

        // 从参数中获取查询语句
        Object query = params.get("query");
        if (query == null) {
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("缺少 query 参数")
                    .build();
        }

        try {
            // 构建请求
            DataAgentQueryRequest request = DataAgentQueryRequest.builder()
                    .tenantId(tenantId)
                    .systemId(systemId)
                    .userId(userId)
                    .query(query.toString())
                    .timeoutMs(timeoutSeconds != null ? timeoutSeconds * 1000L : 60000L)
                    .build();

            // 调用 DataAgent
            long startTime = System.currentTimeMillis();
            DataAgentQueryResponse response = dataAgentClient.query(request);
            long duration = System.currentTimeMillis() - startTime;

            if (!response.isSuccess()) {
                logger.error("DataAgentToolExecutor#execute - query failed, error={}",
                        response.getError());
                return ExecutionResult.builder()
                        .success(false)
                        .errorMessage(response.getError())
                        .durationMs(duration)
                        .build();
            }

            logger.info("DataAgentToolExecutor#execute - completed, rows={}, duration={}ms",
                    response.getRowCount(), duration);

            return ExecutionResult.builder()
                    .success(true)
                    .responseBody(response.getData())
                    .rawResponse(response.getRawData())
                    .durationMs(duration)
                    .build();

        } catch (Exception e) {
            logger.error("DataAgentToolExecutor#execute - execution failed", e);
            return ExecutionResult.builder()
                    .success(false)
                    .errorMessage("DataAgent 查询失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String getSupportedType() {
        return "DATAAGENT";
    }
}
```

### 8.5 集成层 ActionIntentEvaluator

```java
package com.alibaba.assistant.agent.planning.integration.evaluation;

import com.alibaba.assistant.agent.evaluation.evaluator.Evaluator;
import com.alibaba.assistant.agent.evaluation.model.*;
import com.alibaba.assistant.agent.planning.collection.ParamCollectionSession;
import com.alibaba.assistant.agent.planning.context.TenantContext;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ActionMatch;
import com.alibaba.assistant.agent.planning.service.ParamCollectionService;
import com.alibaba.assistant.agent.planning.spi.action.ActionProvider;
import com.alibaba.assistant.agent.planning.spi.permission.PermissionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作意图评估器
 *
 * <p>集成到 evaluation 模块的评估器，负责：
 * <ul>
 * <li>匹配 action（带租户过滤）</li>
 * <li>检查用户权限</li>
 * <li>处理参数收集流程</li>
 * <li>返回评估结果</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
public class ActionIntentEvaluator implements Evaluator {

    private static final Logger logger = LoggerFactory.getLogger(ActionIntentEvaluator.class);
    private static final String EVALUATOR_ID = "action_intent_evaluator";

    private final ActionProvider actionProvider;
    private final ParamCollectionService paramCollectionService;
    private final PermissionProvider permissionProvider;
    private final boolean enableParamCollection;
    private final boolean enablePermissionCheck;

    public ActionIntentEvaluator(ActionProvider actionProvider,
                                  ParamCollectionService paramCollectionService,
                                  PermissionProvider permissionProvider,
                                  boolean enableParamCollection,
                                  boolean enablePermissionCheck) {
        this.actionProvider = actionProvider;
        this.paramCollectionService = paramCollectionService;
        this.permissionProvider = permissionProvider;
        this.enableParamCollection = enableParamCollection;
        this.enablePermissionCheck = enablePermissionCheck;
    }

    @Override
    public String getEvaluatorId() {
        return EVALUATOR_ID;
    }

    @Override
    public CriterionResult evaluate(CriterionExecutionContext executionContext) {
        CriterionResult result = new CriterionResult();
        result.setCriterionName(executionContext.getCriterion().getName());
        result.setStartTimeMillis(System.currentTimeMillis());

        try {
            // 1. 获取用户输入和会话信息
            EvaluationContext inputContext = executionContext.getInputContext();
            String userInput = (String) inputContext.getInputValue("userInput");
            String sessionId = (String) inputContext.getInputValue("sessionId");
            String userId = (String) inputContext.getInputValue("userId");

            if (userInput == null || userInput.isBlank()) {
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue("NO_MATCH");
                return result;
            }

            // 2. 获取租户上下文
            Long tenantId = TenantContext.getTenantId();
            Long systemId = TenantContext.getSystemId();

            logger.debug("ActionIntentEvaluator#evaluate - tenantId={}, systemId={}, userInput={}",
                    tenantId, systemId, userInput);

            // 3. 匹配 actions（已自动过滤租户）
            Map<String, Object> matchContext = buildMatchContext(executionContext);
            List<ActionMatch> matches = actionProvider.matchActions(userInput, matchContext);

            if (CollectionUtils.isEmpty(matches)) {
                logger.debug("ActionIntentEvaluator#evaluate - no action matched");
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue("NO_MATCH");
                return result;
            }

            // 4. 找到第一个有权限的 action
            ActionMatch bestMatch = null;
            for (ActionMatch match : matches) {
                ActionDefinition action = match.getAction();

                // 检查权限
                if (enablePermissionCheck && permissionProvider != null) {
                    PermissionProvider.PermissionCheckResult permResult =
                            permissionProvider.checkPermission(
                                    tenantId, systemId,
                                    Long.valueOf(userId),
                                    action.getActionId(),
                                    action.getAllowedRoles()
                            );

                    if (!permResult.isAllowed()) {
                        logger.debug("ActionIntentEvaluator#evaluate - permission denied, actionId={}, reason={}",
                                action.getActionId(), permResult.getReason());
                        continue;
                    }
                }

                bestMatch = match;
                break;
            }

            if (bestMatch == null) {
                logger.debug("ActionIntentEvaluator#evaluate - no permitted action");
                result.setStatus(CriterionStatus.SUCCESS);
                result.setValue("NO_PERMISSION");
                return result;
            }

            ActionDefinition action = bestMatch.getAction();
            logger.info("ActionIntentEvaluator#evaluate - action matched, actionId={}, confidence={}",
                    action.getActionId(), bestMatch.getConfidence());

            // 5. 参数收集流程
            if (enableParamCollection && paramCollectionService != null && needsParamCollection(action)) {
                return handleParamCollection(action, userInput, sessionId, userId, result);
            }

            // 6. 直接返回匹配结果
            result.setStatus(CriterionStatus.SUCCESS);
            String matchResult = String.format("MATCHED|%s|%s|%.2f|%s",
                    action.getActionId(),
                    action.getActionName(),
                    bestMatch.getConfidence(),
                    bestMatch.getMatchType().name());
            result.setValue(matchResult);
            return result;

        } catch (Exception e) {
            logger.error("ActionIntentEvaluator#evaluate - evaluation failed", e);
            result.setStatus(CriterionStatus.ERROR);
            result.setErrorMessage(e.getMessage());
            result.setValue("NO_MATCH");
        } finally {
            result.setEndTimeMillis(System.currentTimeMillis());
        }

        return result;
    }

    /**
     * 处理参数收集流程
     */
    private CriterionResult handleParamCollection(ActionDefinition action,
                                                   String userInput,
                                                   String sessionId,
                                                   String userId,
                                                   CriterionResult result) {
        // ... 现有参数收集逻辑 ...
        return result;
    }

    /**
     * 检查动作是否需要参数收集
     */
    private boolean needsParamCollection(ActionDefinition action) {
        if (action.getParameters() == null || action.getParameters().isEmpty()) {
            return false;
        }
        return action.getParameters().stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getRequired()));
    }

    /**
     * 构建匹配上下文
     */
    private Map<String, Object> buildMatchContext(CriterionExecutionContext executionContext) {
        Map<String, Object> context = new HashMap<>();
        EvaluationContext evalContext = executionContext.getInputContext();
        if (evalContext.getInput() != null) {
            context.putAll(evalContext.getInput());
        }
        if (evalContext.getEnvironment() != null) {
            context.putAll(evalContext.getEnvironment());
        }
        return context;
    }
}
```

---

## 9. 配置设计

### 9.1 完整配置文件

```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          planning:
            # ========== 基础配置 ==========
            enabled: true

            # ========== 匹配配置 ==========
            matching:
              # 匹配阈值（0-1）
              threshold: 0.3
              # 是否启用语义匹配
              semantic-matching-enabled: false
              # 最大匹配结果数量
              max-matches: 5
              # 关键词权重（0-1）
              keyword-weight: 0.6
              # 语义权重（0-1）
              semantic-weight: 0.4
              # 是否启用参数收集流程
              param-collection-enabled: true

            # ========== 会话存储配置 ==========
            session:
              # 存储类型: IN_MEMORY, REDIS, DATABASE
              provider-type: REDIS
              # Redis 配置
              redis:
                key-prefix: "param:session:"
                expire-after: 1h
                database: 0
              # 内存存储配置
              in-memory:
                max-sessions: 1000
                expire-after-minutes: 60

            # ========== 权限配置 ==========
            permission:
              # 是否启用权限检查
              enabled: true
              # 权限提供者类型: rbac, custom
              provider-type: rbac

            # ========== 租户配置 ==========
            tenant:
              # 是否启用多租户
              enabled: true
              # 默认租户 ID（开发环境）
              default-tenant-id: 1
              # 默认系统 ID（开发环境）
              default-system-id: 1
              # 上下文传播方式: THREAD_LOCAL, REQUEST_HEADER
              context-propagation: THREAD_LOCAL

            # ========== MCP 配置 ==========
            mcp:
              # 是否启用 MCP 支持
              enabled: true
              # MCP Server 配置
              servers:
                # DataAgent MCP Server
                dataagent:
                  uri: "http://localhost:8081/mcp"
                  timeout: 60s
                  enabled: true
                # 其他 MCP Servers
                custom-server:
                  uri: "http://localhost:8082/mcp"
                  timeout: 30s
                  enabled: false

            # ========== 意图识别配置 ==========
            intent:
              # 是否启用意图识别 Hook
              enabled: true
              # 直接执行阈值
              direct-execute-threshold: 0.95
              # 提示注入阈值
              hint-threshold: 0.7
              # 最大匹配候选数
              max-candidates: 5

            # ========== Web API 配置 ==========
            web:
              # 是否启用 Web API
              enabled: false
              # API 基础路径
              base-path: /api/v1/planning
```

### 9.2 配置属性类扩展

```java
package com.alibaba.assistant.agent.planning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Planning 扩展配置属性（扩展版本）
 *
 * @author Assistant Agent Team
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.planning")
public class PlanningExtensionProperties {

    // ========== 现有配置（保持兼容）==========

    private boolean enabled = true;
    private MatchingConfig matching = new MatchingConfig();
    private IntentConfig intent = new IntentConfig();
    private boolean webEnabled = true;
    private long defaultTimeoutMs = 30000;
    private int maxRetries = 3;

    // ========== NEW: 会话存储配置 ==========
    private SessionConfig session = new SessionConfig();

    // ========== NEW: 权限配置 ==========
    private PermissionConfig permission = new PermissionConfig();

    // ========== NEW: 租户配置 ==========
    private TenantConfig tenant = new TenantConfig();

    // ========== NEW: MCP 配置 ==========
    private McpConfig mcp = new McpConfig();

    // ========== Getters and Setters ==========

    // ... 现有 getter/setter ...

    public SessionConfig getSession() {
        return session;
    }

    public void setSession(SessionConfig session) {
        this.session = session;
    }

    public PermissionConfig getPermission() {
        return permission;
    }

    public void setPermission(PermissionConfig permission) {
        this.permission = permission;
    }

    public TenantConfig getTenant() {
        return tenant;
    }

    public void setTenant(TenantConfig tenant) {
        this.tenant = tenant;
    }

    public McpConfig getMcp() {
        return mcp;
    }

    public void setMcp(McpConfig mcp) {
        this.mcp = mcp;
    }

    // ========== 内部配置类 ==========

    /**
     * 会话存储配置
     */
    public static class SessionConfig {
        /**
         * 存储类型: IN_MEMORY, REDIS, DATABASE
         */
        private String providerType = "IN_MEMORY";

        /**
         * Redis 配置
         */
        private RedisConfig redis = new RedisConfig();

        /**
         * 内存存储配置
         */
        private InMemorySessionConfig inMemory = new InMemorySessionConfig();

        // Getters and Setters
        public String getProviderType() {
            return providerType;
        }

        public void setProviderType(String providerType) {
            this.providerType = providerType;
        }

        public RedisConfig getRedis() {
            return redis;
        }

        public void setRedis(RedisConfig redis) {
            this.redis = redis;
        }

        public InMemorySessionConfig getInMemory() {
            return inMemory;
        }

        public void setInMemory(InMemorySessionConfig inMemory) {
            this.inMemory = inMemory;
        }

        public static class RedisConfig {
            private String keyPrefix = "param:session:";
            private String expireAfter = "1h";
            private int database = 0;

            // Getters and Setters
            public String getKeyPrefix() {
                return keyPrefix;
            }

            public void setKeyPrefix(String keyPrefix) {
                this.keyPrefix = keyPrefix;
            }

            public String getExpireAfter() {
                return expireAfter;
            }

            public void setExpireAfter(String expireAfter) {
                this.expireAfter = expireAfter;
            }

            public int getDatabase() {
                return database;
            }

            public void setDatabase(int database) {
                this.database = database;
            }
        }

        public static class InMemorySessionConfig {
            private int maxSessions = 1000;
            private int expireAfterMinutes = 60;

            // Getters and Setters
            public int getMaxSessions() {
                return maxSessions;
            }

            public void setMaxSessions(int maxSessions) {
                this.maxSessions = maxSessions;
            }

            public int getExpireAfterMinutes() {
                return expireAfterMinutes;
            }

            public void setExpireAfterMinutes(int expireAfterMinutes) {
                this.expireAfterMinutes = expireAfterMinutes;
            }
        }
    }

    /**
     * 权限配置
     */
    public static class PermissionConfig {
        private boolean enabled = false;
        private String providerType = "rbac";

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProviderType() {
            return providerType;
        }

        public void setProviderType(String providerType) {
            this.providerType = providerType;
        }
    }

    /**
     * 租户配置
     */
    public static class TenantConfig {
        private boolean enabled = false;
        private Long defaultTenantId = 1L;
        private Long defaultSystemId = 1L;
        private String contextPropagation = "THREAD_LOCAL"; // THREAD_LOCAL, REQUEST_HEADER

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Long getDefaultTenantId() {
            return defaultTenantId;
        }

        public void setDefaultTenantId(Long defaultTenantId) {
            this.defaultTenantId = defaultTenantId;
        }

        public Long getDefaultSystemId() {
            return defaultSystemId;
        }

        public void setDefaultSystemId(Long defaultSystemId) {
            this.defaultSystemId = defaultSystemId;
        }

        public String getContextPropagation() {
            return contextPropagation;
        }

        public void setContextPropagation(String contextPropagation) {
            this.contextPropagation = contextPropagation;
        }
    }

    /**
     * MCP 配置
     */
    public static class McpConfig {
        private boolean enabled = false;
        private java.util.Map<String, ServerConfig> servers = new java.util.HashMap<>();

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public java.util.Map<String, ServerConfig> getServers() {
            return servers;
        }

        public void setServers(java.util.Map<String, ServerConfig> servers) {
            this.servers = servers;
        }

        @lombok.Data
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class ServerConfig {
            private String uri;
            private String timeout = "30s";
            private boolean enabled = true;
        }
    }
}
```

---

## 10. 数据库设计

### 10.1 扩展 action_registry 表

```sql
-- ============================================================
-- 扩展 action_registry 表 - 添加租户和权限支持
-- ============================================================

ALTER TABLE action_registry
-- 租户相关字段
ADD COLUMN tenant_id BIGINT COMMENT '租户ID，null表示全局action',
ADD COLUMN system_id BIGINT COMMENT '系统ID，null表示租户级action',
ADD COLUMN module_id BIGINT COMMENT '模块ID，null表示系统级action',

-- 权限相关字段
ADD COLUMN allowed_roles JSON COMMENT '允许的角色列表',
ADD COLUMN data_permission_config JSON COMMENT '数据权限配置',

-- 索引
ADD INDEX idx_tenant_system (tenant_id, system_id),
ADD INDEX idx_tenant_system_module (tenant_id, system_id, module_id);

-- 更新现有数据为全局 action（可选）
-- UPDATE action_registry SET tenant_id = NULL WHERE tenant_id IS NOT NULL;
```

### 10.2 新增参数收集会话表

```sql
-- ============================================================
-- 参数收集会话表
-- ============================================================

CREATE TABLE IF NOT EXISTS param_collection_session (
    -- 主键
    session_id VARCHAR(64) PRIMARY KEY COMMENT '会话ID（UUID）',

    -- Action 信息
    action_id VARCHAR(100) NOT NULL COMMENT 'Action ID',

    -- 会话关联
    assistant_session_id VARCHAR(100) COMMENT 'Assistant Agent 会话ID',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',

    -- 租户信息
    tenant_id BIGINT COMMENT '租户ID',
    system_id BIGINT COMMENT '系统ID',

    -- 会话状态
    state VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '状态：INIT, COLLECTING, PENDING_CONFIRM, CONFIRMED, EXECUTING, COMPLETED, CANCELLED, EXPIRED, FAILED',

    -- 参数数据
    collected_params JSON COMMENT '已收集的参数',
    missing_params JSON COMMENT '缺失的参数列表',

    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',

    -- 执行结果
    execution_result JSON COMMENT '执行结果',
    error_message TEXT COMMENT '错误信息',

    -- 索引
    INDEX idx_assistant_session (assistant_session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_system (tenant_id, system_id),
    INDEX idx_state (state),
    INDEX idx_expires_at (expires_at),
    INDEX idx_action_id (action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='参数收集会话表';

-- ============================================================
-- MCP Server 配置表（可选）
-- ============================================================

CREATE TABLE IF NOT EXISTS mcp_server_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    server_name VARCHAR(100) NOT NULL UNIQUE COMMENT 'Server名称',
    server_type VARCHAR(50) NOT NULL COMMENT 'Server类型：DATAGENET, CUSTOM',
    uri VARCHAR(500) NOT NULL COMMENT 'Server URI',
    timeout_seconds INT DEFAULT 30 COMMENT '超时时间（秒）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    config JSON COMMENT '额外配置',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_server_name (server_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP Server配置表';

-- 插入默认 DataAgent MCP Server 配置
INSERT INTO mcp_server_config (server_name, server_type, uri, timeout_seconds, enabled)
VALUES ('dataagent', 'DATAGENET', 'http://localhost:8081/mcp', 60, 1)
ON DUPLICATE KEY UPDATE uri = VALUES(uri);
```

### 10.3 数据迁移脚本

```sql
-- ============================================================
-- 数据迁移脚本 v1.0 -> v2.0
-- ============================================================

-- 1. 添加新字段
ALTER TABLE action_registry
ADD COLUMN tenant_id BIGINT,
ADD COLUMN system_id BIGINT,
ADD COLUMN module_id BIGINT,
ADD COLUMN allowed_roles JSON,
ADD COLUMN data_permission_config JSON;

-- 2. 创建索引
ALTER TABLE action_registry
ADD INDEX idx_tenant_system (tenant_id, system_id),
ADD INDEX idx_tenant_system_module (tenant_id, system_id, module_id);

-- 3. 更新现有数据为全局 action
UPDATE action_registry SET tenant_id = NULL WHERE tenant_id IS NOT NULL;

-- 4. 创建参数收集会话表
-- ... (见上面的 CREATE TABLE 语句)

-- 5. 创建 MCP Server 配置表
-- ... (见上面的 CREATE TABLE 语句)
```

---

## 11. 兼容性与迁移

### 11.1 向后兼容性

#### API 兼容

**保持兼容的类**:
- `ActionDefinition` - 添加新字段，现有字段不变
- `ActionMatch` - 无变化
- `ActionParameter` - 无变化
- `StepDefinition` - 无变化
- `ActionProvider` SPI - 添加新方法，现有方法保持
- `ActionRepository` SPI - 无变化

**需要迁移的类**:
- `ActionIntentEvaluator` - 从 `core` 移到 `integration`
- `ParamCollectionSession` - 添加租户字段（可选）
- `ActionExecutor` - 重构为 SPI

#### 配置兼容

**保持兼容的配置**:
```yaml
spring.ai.alibaba.codeact.extension.planning:
  enabled: true
  matching:
    threshold: 0.3
    param-collection-enabled: true
```

**新增配置（可选）**:
```yaml
spring.ai.alibaba.codeact.extension.planning:
  session:
    provider-type: REDIS
  tenant:
    enabled: true
  permission:
    enabled: true
```

### 11.2 迁移指南

#### 阶段 1: 准备（无破坏性变更）

1. **添加新模块**
   - 创建 `assistant-agent-planning-integration` 模块
   - 添加新的 SPI 接口

2. **扩展数据模型**
   - `ActionDefinition` 添加租户字段（可选，默认 null）
   - `ParamCollectionSession` 添加租户字段（可选）

3. **数据库扩展**
   - 执行 ALTER TABLE 添加新字段
   - 创建新表（不影响现有功能）

#### 阶段 2: 功能扩展（保持现有功能）

1. **添加 SPI 实现**
   - `RedisSessionProvider`
   - `RbacPermissionProvider`
   - `McpActionExecutor`

2. **添加新组件**
   - `TenantContext`
   - `ActionExecutorFactory`
   - `McpClientManager`

#### 阶段 3: 逐步迁移（可选）

1. **迁移 ActionIntentEvaluator**
   - 从 `core` 移到 `integration`
   - 更新依赖引用

2. **重构 ActionExecutor**
   - 使用工厂模式
   - 拆分为独立执行器

3. **启用新功能**
   - 配置 Redis 存储
   - 启用权限检查
   - 配置租户隔离

#### 阶段 4: 清理（可选）

1. 移除已弃用的代码
2. 更新文档和示例

### 11.3 兼容性检查清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| API 兼容 | ✅ | 所有公共 API 保持兼容 |
| 配置兼容 | ✅ | 新增配置均有默认值 |
| 数据兼容 | ✅ | 数据库字段可选，不影响现有数据 |
| 行为兼容 | ✅ | 新功能默认关闭，需手动启用 |
| 依赖兼容 | ⚠️ | 新增 Redis 依赖（可选） |

---

## 12. 测试策略

### 12.1 单元测试

| 测试范围 | 测试类 | 覆盖率目标 |
|----------|--------|------------|
| 租户过滤 | `TenantAwareActionProviderTest` | 90% |
| 会话存储 | `RedisSessionProviderTest` | 85% |
| 权限检查 | `RbacPermissionProviderTest` | 90% |
| MCP 执行器 | `McpActionExecutorTest` | 80% |
| 上下文管理 | `TenantContextTest` | 95% |

### 12.2 集成测试

| 测试场景 | 描述 |
|----------|------|
| 租户隔离 | 验证不同租户的 action 不会互相干扰 |
| 会话持久化 | 验证 Redis 存储的会话可以正确恢复 |
| 权限验证 | 验证无权限用户无法执行 action |
| MCP 调用 | 验证通过 MCP 调用 DataAgent |
| 多租户匹配 | 验证 action 匹配时正确过滤租户 |

### 12.3 性能测试

| 测试项 | 目标 |
|--------|------|
| Action 匹配延迟 | < 100ms (P99) |
| 会话存储延迟 | < 10ms (P99) |
| 权限检查延迟 | < 50ms (P99) |
| MCP 调用延迟 | < 500ms (P99) |

---

## 13. 实施计划

### 13.1 阶段划分

| 阶段 | 任务 | 优先级 | 预估工作量 |
|------|------|--------|------------|
| **Phase 1** | SPI 接口设计 | P0 | 2 天 |
| **Phase 2** | 数据模型扩展 | P0 | 1 天 |
| **Phase 3** | 租户上下文实现 | P0 | 1 天 |
| **Phase 4** | SessionProvider 实现 | P1 | 2 天 |
| **Phase 5** | PermissionProvider 实现 | P1 | 2 天 |
| **Phase 6** | ActionExecutor 重构 | P1 | 2 天 |
| **Phase 7** | MCP 执行器实现 | P1 | 3 天 |
| **Phase 8** | 集成层重构 | P2 | 2 天 |
| **Phase 9** | 单元测试 | P0 | 3 天 |
| **Phase 10** | 集成测试 | P0 | 2 天 |
| **Phase 11** | 文档更新 | P1 | 1 天 |
| **Phase 12** | 发布和部署 | P1 | 1 天 |

**总计**: 约 22 天

### 13.2 依赖关系

```
Phase 1 (SPI 设计)
    ↓
Phase 2 (数据模型)
    ↓
Phase 3 (租户上下文) ←→ Phase 4 (SessionProvider)
    ↓                     ↓
Phase 5 (PermissionProvider)
    ↓
Phase 6 (ActionExecutor) ←→ Phase 7 (MCP 执行器)
    ↓
Phase 8 (集成层)
    ↓
Phase 9 (单元测试) ←→ Phase 10 (集成测试)
    ↓
Phase 11 (文档)
    ↓
Phase 12 (发布)
```

### 13.3 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Redis 依赖 | 部署复杂度增加 | 提供内存存储作为后备 |
| 向后兼容性 | 破坏现有功能 | 严格测试，保持 API 兼容 |
| MCP 实现 | DataAgent 集成失败 | 提供 Mock 实现用于测试 |
| 性能回归 | 匹配延迟增加 | 性能测试，优化查询 |

---

## 14. 附录

### 14.1 术语表

| 术语 | 说明 |
|------|------|
| **Action** | 可执行的动作定义，包含参数、接口绑定等 |
| **ActionProvider** | Action 提供者 SPI，负责匹配和检索 actions |
| **SessionProvider** | 会话存储提供者 SPI |
| **PermissionProvider** | 权限验证提供者 SPI |
| **TenantContext** | 租户上下文，ThreadLocal 存储 |
| **MCP** | Model Context Protocol，工具调用协议 |
| **DataAgent** | Spring AI Alibaba 的数据查询 Agent |

### 14.2 参考文档

- [Assistant Agent 官方文档](https://java2ai.com/agents/assistantagent/quick-start)
- [DataAgent 官方文档](https://java2ai.com/agents/dataagent/quick-start)
- [MCP 协议规范](https://modelcontextprotocol.io/)
- [Spring Boot 配置属性](https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html)

### 14.3 版本历史

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| v1.0 | 2026-01-16 | Assistant Agent Team | 初始版本 |

---

**文档结束**
