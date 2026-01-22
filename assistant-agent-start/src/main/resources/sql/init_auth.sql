-- ============================================================================
-- 用户认证系统数据库初始化脚本
-- ============================================================================

-- 创建用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `roles` VARCHAR(255) DEFAULT NULL COMMENT '角色（逗号分隔）',
    `permissions` VARCHAR(500) DEFAULT NULL COMMENT '权限（逗号分隔）',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 插入默认管理员账户（密码: admin）
-- 注意：密码已使用 BCrypt 加密
INSERT INTO `sys_user` (`username`, `password`, `email`, `roles`, `permissions`, `enabled`)
VALUES (
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    'admin@example.com',
    'ADMIN,USER',
    'read,write,delete,execute',
    1
) ON DUPLICATE KEY UPDATE `username` = `username`;
