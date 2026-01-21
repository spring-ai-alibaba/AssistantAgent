-- ============================================================================
-- Multi-System Permission Init Data
-- Assistant Agent Platform
-- ============================================================================
-- Description: Initial demo data for multi-system integration
-- Version: 1.0.0
-- Author: Assistant Agent Team
-- ============================================================================

-- ============================================================================
-- 1. 初始化外部系统配置
-- ============================================================================
INSERT INTO external_system_config (system_id, system_name, system_type, api_base_url, auth_type, auth_config, adapter_class, description, enabled) VALUES
('oa-system', 'OA办公系统', 'OA', 'http://localhost:8081/api', 'API_KEY',
 '{"apiKey": "oa-demo-key", "headerName": "X-API-Key"}',
 'com.alibaba.assistant.agent.planning.permission.adapter.OaPermissionAdapter',
 '企业OA办公系统，包含考勤、任务管理等功能', 1),

('gov-platform', '政务服务平台', 'GOV', 'http://localhost:8082/api', 'API_KEY',
 '{"apiKey": "gov-demo-key", "headerName": "X-API-Key"}',
 'com.alibaba.assistant.agent.planning.permission.adapter.GovPermissionAdapter',
 '政务服务平台，提供业务办理、预约、内容管理等功能', 1);

-- ============================================================================
-- 2. 初始化Demo用户
-- ============================================================================
-- 密码都是: demo123 (BCrypt加密)
INSERT INTO platform_user (id, username, password_hash, name, phone, email, status) VALUES
('U001', 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '张三', '13800000001', 'zhangsan@demo.com', 'ACTIVE'),
('U002', 'lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '李四', '13800000002', 'lisi@demo.com', 'ACTIVE'),
('U003', 'wangwu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '王五', '13800000003', 'wangwu@demo.com', 'ACTIVE'),
('U004', 'zhaoliu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '赵六', '13800000004', 'zhaoliu@demo.com', 'ACTIVE'),
('U005', 'sunqi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iA7Rw0o6rRBwDYb5GYA.MH1y.SdC', '孙七', '13800000005', 'sunqi@demo.com', 'ACTIVE');

-- ============================================================================
-- 3. 初始化用户角色
-- ============================================================================
INSERT INTO platform_user_role (id, user_id, role_code) VALUES
('R001', 'U001', 'ROLE_USER'),
('R002', 'U002', 'ROLE_USER'),
('R003', 'U002', 'ROLE_MANAGER'),
('R004', 'U003', 'ROLE_USER'),
('R005', 'U003', 'ROLE_MANAGER'),
('R006', 'U003', 'ROLE_ADMIN'),
('R007', 'U004', 'ROLE_USER'),
('R008', 'U005', 'ROLE_USER');

-- ============================================================================
-- 4. 初始化身份映射（Demo数据）
-- ============================================================================
-- 张三：OA系统员工，政务平台群众
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M001', 'U001', 'oa-system', 'zhang.san@company.com', '张三',
 '{"role": "employee", "deptId": "tech-001", "deptName": "技术部"}', 'MANUAL'),
('M002', 'U001', 'gov-platform', '320102199001011234', '张三',
 '{"userType": "citizen"}', 'MANUAL');

-- 李四：OA系统经理
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M003', 'U002', 'oa-system', 'li.si@company.com', '李四',
 '{"role": "manager", "deptId": "tech-001", "deptName": "技术部"}', 'MANUAL');

-- 王五：OA系统大领导，政务平台分管领导
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M004', 'U003', 'oa-system', 'wang.wu@company.com', '王五',
 '{"role": "director"}', 'MANUAL'),
('M005', 'U003', 'gov-platform', 'leader_001', '王五',
 '{"userType": "leader", "bureauId": "civil-affairs", "bureauName": "民政局"}', 'MANUAL');

-- 赵六：政务平台业务处理人员
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M006', 'U004', 'gov-platform', 'staff_001', '赵六',
 '{"userType": "staff", "bureauId": "civil-affairs", "bureauName": "民政局", "level": 2}', 'MANUAL');

-- 孙七：OA系统员工（销售部）
INSERT INTO user_identity_mapping (id, platform_user_id, system_id, external_user_id, external_username, extra_info, bind_type) VALUES
('M007', 'U005', 'oa-system', 'sun.qi@company.com', '孙七',
 '{"role": "employee", "deptId": "sales-001", "deptName": "销售部"}', 'MANUAL');

-- ============================================================================
-- 5. 初始化Action权限配置
-- ============================================================================
-- OA系统Actions
INSERT INTO action_permission_config (id, action_id, filter_mapping, scope_field, enforced) VALUES
('APC001', 'oa:attendance:apply-leave', '{"userId": "applicantId"}', 'applicantId', 1),
('APC002', 'oa:attendance:query-late', '{"departmentId": "departmentId"}', 'departmentId', 1),
('APC003', 'oa:attendance:query-statistics', '{}', NULL, 1),
('APC004', 'oa:task:update-status', '{"userId": "assigneeId"}', 'assigneeId', 1),
('APC005', 'oa:task:query-progress', '{"departmentId": "departmentId"}', 'departmentId', 1),
('APC006', 'oa:task:query-statistics', '{}', NULL, 1);

-- 政务平台Actions
INSERT INTO action_permission_config (id, action_id, filter_mapping, scope_field, enforced) VALUES
('APC007', 'gov:service:query-process', '{}', NULL, 0),
('APC008', 'gov:appointment:create', '{"citizenId": "citizenId"}', 'citizenId', 1),
('APC009', 'gov:appointment:set-limit', '{"bureauId": "bureauId"}', 'bureauId', 1),
('APC010', 'gov:content:publish-article', '{"bureauId": "bureauId"}', 'bureauId', 1),
('APC011', 'gov:stats:query-appointment', '{"bureauId": "bureauId"}', 'bureauId', 1),
('APC012', 'gov:stats:query-satisfaction', '{"bureauId": "bureauId"}', 'bureauId', 1);

-- ============================================================================
-- 6. 权限矩阵说明
-- ============================================================================
--
-- OA系统权限矩阵:
-- +----------+------------------------+---------------------------+---------------+
-- | 角色     | 允许的Actions          | 数据范围                  | 过滤条件      |
-- +----------+------------------------+---------------------------+---------------+
-- | employee | apply-leave            | SELF                      | userId=自己   |
-- |          | update-status          | SELF                      | userId=自己   |
-- +----------+------------------------+---------------------------+---------------+
-- | manager  | apply-leave            | SELF                      | userId=自己   |
-- |          | query-late             | DEPARTMENT                | deptId=本部门 |
-- |          | update-status          | SELF                      | userId=自己   |
-- |          | query-progress         | DEPARTMENT                | deptId=本部门 |
-- +----------+------------------------+---------------------------+---------------+
-- | director | query-late             | ORGANIZATION              | 无            |
-- |          | query-statistics       | ORGANIZATION              | 无            |
-- |          | query-progress         | ORGANIZATION              | 无            |
-- |          | query-statistics       | ORGANIZATION              | 无            |
-- +----------+------------------------+---------------------------+---------------+
--
-- 政务平台权限矩阵:
-- +----------+------------------------+---------------------------+---------------+
-- | 角色     | 允许的Actions          | 数据范围                  | 过滤条件      |
-- +----------+------------------------+---------------------------+---------------+
-- | citizen  | query-process          | 公开                      | 无            |
-- |          | appointment:create     | SELF                      | citizenId=自己|
-- +----------+------------------------+---------------------------+---------------+
-- | staff    | appointment:set-limit  | DEPARTMENT                | bureauId=本局 |
-- |          | content:publish        | DEPARTMENT                | bureauId=本局 |
-- +----------+------------------------+---------------------------+---------------+
-- | leader   | stats:query-appointment| DEPARTMENT_TREE           | bureauId=分管 |
-- |          | stats:query-satisfaction| DEPARTMENT_TREE          | bureauId=分管 |
-- +----------+------------------------+---------------------------+---------------+
