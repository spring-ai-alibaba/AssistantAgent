-- ============================================================================
-- OA请假集成 - Action Registry扩展
-- 基于System的Handler架构实现
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 扩展 action_registry 表，添加 system_id 字段
-- ----------------------------------------------------------------------------

-- 检查并添加system_id字段
ALTER TABLE `action_registry`
ADD COLUMN IF NOT EXISTS `system_id` VARCHAR(100) COMMENT '所属系统ID（关联external_system_config.system_id）' AFTER `action_type`;

-- 添加索引
ALTER TABLE `action_registry`
ADD INDEX IF NOT EXISTS `idx_system_id` (`system_id`);

-- ----------------------------------------------------------------------------
-- 2. 更新 external_system_config 中的OA系统配置
-- ----------------------------------------------------------------------------

-- 更新OA系统的认证配置为SESSION类型
UPDATE `assistant_agent`.`external_system_config`
SET
  `auth_type` = 'SESSION',
  `auth_config` = JSON_OBJECT(
    'type', 'CUSTOM',
    'sessionEndpoint', '/api/oa_integration/get_phpsessid',
    'sessionCacheEnabled', true,
    'sessionCacheTtl', 7200,
    'cookieName', 'PHPSESSID'
  ),
  `api_base_url` = 'http://office.test',
  `updated_at` = NOW()
WHERE `system_id` = 'oa-system';

-- 验证更新
SELECT
  system_id,
  system_name,
  api_base_url,
  auth_type,
  JSON_PRETTY(auth_config) as auth_config
FROM `assistant_agent`.`external_system_config`
WHERE `system_id` = 'oa-system';

-- ----------------------------------------------------------------------------
-- 3. 创建OA请假Action Registry记录
-- ----------------------------------------------------------------------------

-- OA请假申请
INSERT INTO `action_registry` (
  `system_id`,
  `action_id`,
  `action_name`,
  `description`,
  `keywords`,
  `synonyms`,
  `example_inputs`,
  `parameters`,
  `action_type`,
  `handler`,
  `interface_binding`,
  `category`,
  `tags`,
  `priority`,
  `enabled`
) VALUES
(
  'oa-system',
  'oa:leave:request',
  '提交OA请假申请',
  '向OA系统提交请假申请，支持事假、病假、年假、调休。需要指定请假时间、类型、理由和审批人。',
  '["请假", "申请请假", "我要请假", "想请假", "提交请假", "请个假", "想申请休假", "明天不来", "需要请假"]',
  '["请个假", "想申请休假", "明天不来", "需要请假"]',
  '["我想明天请假一天", "申请后天到后天事假", "我要请假，理由是家中有事", "明天请病假", "下周请年假"]',
  JSON_ARRAY(
    JSON_OBJECT(
      'name', 'start_date',
      'type', 'STRING',
      'label', '开始日期',
      'required', true,
      'description', '请假开始日期，格式: yyyy-MM-dd HH:mm',
      'examples', JSON_ARRAY('2026-01-21 09:00', '2026-01-21 00:00', '明天 09:00')
    ),
    JSON_OBJECT(
      'name', 'end_date',
      'type', 'STRING',
      'label', '结束日期',
      'required', true,
      'description', '请假结束日期，格式: yyyy-MM-dd HH:mm',
      'examples', JSON_ARRAY('2026-01-22 18:00', '2026-01-22 00:00', '后天 18:00')
    ),
    JSON_OBJECT(
      'name', 'types',
      'type', 'INTEGER',
      'label', '请假类型',
      'required', true,
      'description', '请假类型：1=事假，2=病假，3=年假，4=调休',
      'defaultValue', '1',
      'enum', JSON_ARRAY(
        JSON_OBJECT('label', '事假', 'value', '1'),
        JSON_OBJECT('label', '病假', 'value', '2'),
        JSON_OBJECT('label', '年假', 'value', '3'),
        JSON_OBJECT('label', '调休', 'value', '4')
      )
    ),
    JSON_OBJECT(
      'name', 'reason',
      'type', 'STRING',
      'label', '请假理由',
      'required', true,
      'description', '请详细说明请假原因',
      'validation', JSON_OBJECT('minLength', 2, 'maxLength', 200),
      'examples', JSON_ARRAY('家中有事', '身体不适', '休息调整', '个人事务')
    ),
    JSON_OBJECT(
      'name', 'check_uids',
      'type', 'STRING',
      'label', '审批人ID',
      'required', true,
      'description', '审批人的OA用户ID（多个用逗号分隔）',
      'promptHint', '请指定审批人（如：张三，ID=2）',
      'examples', JSON_ARRAY('2', '2,3')
    )
  ),
  'API_CALL',
  'oaSystemHandler',
  JSON_OBJECT(
    'type', 'HTTP',
    'endpoint', '/home/leaves/add',
    'method', 'POST',
    'contentType', 'application/x-www-form-urlencoded',
    'headers', JSON_OBJECT(
      'X-Requested-With', 'XMLHttpRequest',
      'Accept', '*/*'
    ),
    'parameterMapping', JSON_OBJECT(
      'start_date', 'start_date',
      'end_date', 'end_date',
      'types', 'types',
      'reason', 'reason',
      'check_uids', 'check_uids',
      'flow_id', '1',
      'check_copy_uids', '0',
      'id', '0'
    ),
    'autoCalculate', JSON_ARRAY('duration')
  ),
  'oa-leave',
  JSON_ARRAY('OA', '请假', 'HR', '审批'),
  10,
  1
)
ON DUPLICATE KEY UPDATE
  `action_name` = VALUES(`action_name`),
  `description` = VALUES(`description`),
  `handler` = VALUES(`handler`),
  `interface_binding` = VALUES(`interface_binding`),
  `updated_at` = NOW();

-- ----------------------------------------------------------------------------
-- 4. 验证数据
-- ----------------------------------------------------------------------------

-- 查看已创建的OA请假Action
SELECT
  action_id,
  action_name,
  system_id,
  handler,
  category,
  enabled,
  priority,
  JSON_PRETTY(interface_binding) as binding_config
FROM `action_registry`
WHERE `system_id` = 'oa-system';

-- 查看OA系统配置
SELECT
  system_id,
  system_name,
  api_base_url,
  auth_type,
  JSON_PRETTY(auth_config) as auth_config,
  enabled
FROM `assistant_agent`.`external_system_config`
WHERE `system_id` = 'oa-system';

-- ----------------------------------------------------------------------------
-- 5. 示例：添加更多OA系统Action（可选）
-- ----------------------------------------------------------------------------

/*
-- OA审批处理（示例）
INSERT INTO `action_registry` (
  `system_id`,
  `action_id`,
  `action_name`,
  `description`,
  `keywords`,
  `parameters`,
  `action_type`,
  `handler`,
  `interface_binding`,
  `category`,
  `enabled`
) VALUES
(
  'oa-system',
  'oa:approve:process',
  'OA审批处理',
  '处理OA审批申请（通过/拒绝）',
  '["审批", "通过审批", "拒绝审批", "处理审批"]',
  JSON_ARRAY(
    JSON_OBJECT('name', 'approval_id', 'type', 'INTEGER', 'label', '审批ID', 'required', true),
    JSON_OBJECT('name', 'action', 'type', 'STRING', 'label', '操作', 'required', true, 'enum', JSON_ARRAY('approved', 'rejected'))
  ),
  'API_CALL',
  'oaSystemHandler',
  JSON_OBJECT(
    'type', 'HTTP',
    'endpoint', '/home/approve/process',
    'method', 'POST',
    'parameterMapping', JSON_OBJECT('id', 'approval_id', 'status', 'action')
  ),
  'oa-approve',
  1
);
*/

-- ============================================================================
-- 说明
-- ============================================================================
-- 1. system_id 关联 external_system_config 表
-- 2. handler 指向统一的 SystemHandler Bean（如: oaSystemHandler）
-- 3. interface_binding 定义具体的API endpoint和参数映射
-- 4. autoCalculate 自动计算字段（如duration）
-- 5. 同一系统可以有多个Action，共享同一个Handler
-- ============================================================================
