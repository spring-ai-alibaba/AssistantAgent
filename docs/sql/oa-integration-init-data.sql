-- ============================================================================
-- OA系统集成 - 初始化数据脚本
-- Assistant Agent Platform
-- ============================================================================
-- Description: 插入示例数据和配置
-- Version: 1.0.0
-- Author: Assistant Agent Team
-- ============================================================================

-- ============================================================================
-- 1. 初始化外部系统配置
-- ============================================================================
INSERT INTO external_system_config (system_id, system_name, system_type, api_base_url, auth_type, auth_config, adapter_class, description, enabled) VALUES
('oa-system', 'OA办公系统', 'OA', 'http://localhost:8081', 'API_KEY',
 '{"apiKey": "oa-demo-key", "headerName": "X-API-Key"}',
 'com.alibaba.assistant.agent.planning.permission.adapter.OaPermissionAdapterEnhanced',
 '企业OA办公系统，包含考勤、任务管理等功能', 1)

ON DUPLICATE KEY UPDATE
  system_name = VALUES(system_name),
  api_base_url = VALUES(api_base_url),
  updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 2. 初始化Demo平台用户（密码: demo123）
-- 密码哈希: BCrypt加密后的demo123
-- ============================================================================
INSERT INTO platform_user (id, username, password_hash, name, phone, email, status) VALUES
('U001', 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '张三', '13800000001', 'zhangsan@demo.com', 'ACTIVE'),
('U002', 'lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '李四', '13800000002', 'lisi@demo.com', 'ACTIVE'),
('U003', 'wangwu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '王五', '13800000003', 'wangwu@demo.com', 'ACTIVE')

ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 3. 初始化用户角色
-- ============================================================================
INSERT INTO platform_user_role (id, user_id, role_code) VALUES
('R001', 'U001', 'ROLE_USER'),
('R002', 'U002', 'ROLE_USER'),
('R003', 'U003', 'ROLE_USER')

ON DUPLICATE KEY UPDATE
  role_code = VALUES(role_code);

-- ============================================================================
-- 4. 初始化身份映射（示例数据）
-- 注意：external_user_id需要与OA系统的用户ID对应
-- ============================================================================
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M001', 'U001', 'oa-system', '1', 'admin', '{"role": "admin", "deptId": "1", "deptName": "总公司"}', 'MANUAL'),
('M002', 'U002', 'oa-system', '2', 'zhangsan', '{"role": "manager", "deptId": "10", "deptName": "技术部"}', 'MANUAL'),
('M003', 'U003', 'oa-system', '3', 'lisi', '{"role": "employee", "deptId": "10", "deptName": "技术部"}', 'MANUAL')

ON DUPLICATE KEY UPDATE
  external_user_id = VALUES(external_user_id),
  extra_info = VALUES(extra_info),
  bind_time = CURRENT_TIMESTAMP;

-- ============================================================================
-- 5. 初始化Action权限配置（OA相关）
-- ============================================================================
INSERT INTO action_permission_config (id, action_id, filter_mapping, scope_field, enforced) VALUES
('APC001', 'oa:attendance:apply-leave', '{"userId": "applicantId"}', 'applicantId', 1),
('APC002', 'oa:attendance:query-late', '{"departmentId": "departmentId"}', 'departmentId', 1),
('APC003', 'oa:task:update-status', '{"userId": "assigneeId"}', 'assigneeId', 1),
('APC004', 'oa:task:query-progress', '{"departmentId": "departmentId"}', 'departmentId', 1)

ON DUPLICATE KEY UPDATE
  filter_mapping = VALUES(filter_mapping);

-- ============================================================================
-- 验证数据
-- ============================================================================

-- 查看系统配置
-- SELECT * FROM external_system_config;

-- 查看用户
-- SELECT * FROM platform_user;

-- 查看身份映射
-- SELECT * FROM user_identity_mapping;

-- 查看完整绑定信息
-- SELECT
--     m.id,
--     m.platform_user_id,
--     p.name AS platform_user_name,
--     m.system_id,
--     m.external_user_id,
--     m.external_username,
--     JSON_EXTRACT(m.extra_info, '$.role') AS role,
--     JSON_EXTRACT(m.extra_info, '$.deptName') AS dept_name
-- FROM user_identity_mapping m
-- LEFT JOIN platform_user p ON m.platform_user_id = p.id
-- WHERE m.system_id = 'oa-system';
