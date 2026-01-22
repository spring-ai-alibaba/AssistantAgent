-- ============================================================================
-- OA系统集成 - MySQL数据库初始化脚本
-- Assistant Agent Platform
-- ============================================================================
-- Description: 创建OA集成所需的数据库表
-- Version: 1.0.0
-- Author: Assistant Agent Team
-- ============================================================================

-- 注意：此脚本会创建新表，请确保在 assistant_agent 数据库中执行

-- ============================================================================
-- 1. 平台用户表
-- ============================================================================
CREATE TABLE IF NOT EXISTS platform_user (
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
    INDEX idx_email (email),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户表';

-- ============================================================================
-- 2. 平台用户角色关联表
-- ============================================================================
CREATE TABLE IF NOT EXISTS platform_user_role (
    id VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_user_role (user_id, role_code),
    INDEX idx_user_id (user_id),
    INDEX idx_role_code (role_code),

    CONSTRAINT fk_role_user FOREIGN KEY (user_id)
        REFERENCES platform_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户角色关联表';

-- ============================================================================
-- 3. 外部系统配置表
-- ============================================================================
CREATE TABLE IF NOT EXISTS external_system_config (
    system_id VARCHAR(64) PRIMARY KEY COMMENT '系统ID',
    system_name VARCHAR(100) NOT NULL COMMENT '系统名称',
    system_type VARCHAR(50) COMMENT '系统类型: OA/GOV/ERP/CRM',
    api_base_url VARCHAR(500) COMMENT 'API基础地址',
    auth_type VARCHAR(50) COMMENT '认证类型: API_KEY/OAUTH/BASIC',
    auth_config JSON COMMENT '认证配置',
    adapter_class VARCHAR(255) COMMENT '权限适配器类名',
    icon_url VARCHAR(500) COMMENT '系统图标URL',
    description VARCHAR(500) COMMENT '系统描述',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_system_type (system_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部系统配置表';

-- ============================================================================
-- 4. 用户身份映射表（核心表）
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_identity_mapping (
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
    INDEX idx_system_id (system_id),
    INDEX idx_external_user (system_id, external_user_id),

    CONSTRAINT fk_mapping_user FOREIGN KEY (platform_user_id)
        REFERENCES platform_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_mapping_system FOREIGN KEY (system_id)
        REFERENCES external_system_config(system_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户身份映射表';

-- ============================================================================
-- 5. Action数据权限配置表
-- ============================================================================
CREATE TABLE IF NOT EXISTS action_permission_config (
    id VARCHAR(64) PRIMARY KEY COMMENT 'ID',
    action_id VARCHAR(100) NOT NULL COMMENT 'Action ID',
    filter_mapping JSON COMMENT '过滤字段映射',
    scope_field VARCHAR(100) COMMENT '数据范围字段',
    enforced TINYINT(1) DEFAULT 1 COMMENT '是否强制校验',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_action_id (action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Action数据权限配置表';

-- ============================================================================
-- 6. 权限操作日志表（审计用）
-- ============================================================================
CREATE TABLE IF NOT EXISTS permission_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    platform_user_id VARCHAR(64) COMMENT '平台用户ID',
    system_id VARCHAR(64) COMMENT '外部系统ID',
    external_user_id VARCHAR(255) COMMENT '外部用户ID',
    action_id VARCHAR(100) COMMENT 'Action ID',
    operation_type VARCHAR(50) COMMENT '操作类型: CHECK/INJECT/EXECUTE',
    permission_result VARCHAR(20) COMMENT '权限结果: ALLOWED/DENIED',
    data_scope VARCHAR(50) COMMENT '数据范围',
    filters JSON COMMENT '应用的过滤条件',
    request_params JSON COMMENT '请求参数',
    error_message VARCHAR(1000) COMMENT '错误信息',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT 'User Agent',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_platform_user (platform_user_id),
    INDEX idx_system_id (system_id),
    INDEX idx_action_id (action_id),
    INDEX idx_created_at (created_at),
    INDEX idx_permission_result (permission_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限操作日志表';

-- ============================================================================
-- 验证表结构
-- ============================================================================
SHOW TABLES;
