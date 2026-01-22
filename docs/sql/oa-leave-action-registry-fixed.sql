-- ============================================================================
-- OA请假集成 - Action Registry扩展（修正版）
-- 修正interface_binding格式以符合Java类结构
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
-- 2. 创建OA请假Action Registry记录（修正版）
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
  '向OA系统提交请假申请，支持事假、病假、年假、调休',
  '["请假", "申请请假", "我要请假", "想请假"]',
  '["请个假", "想申请休假", "明天不来"]',
  '["我想明天请假一天", "申请后天到后天事假", "我要请假，理由是家中有事"]',
  JSON_OBJECT(
    'parameters', JSON_ARRAY(
      JSON_OBJECT('name', 'start_date', 'type', 'STRING', 'label', '开始日期', 'required', true, 'description', '格式: yyyy-MM-dd HH:mm'),
      JSON_OBJECT('name', 'end_date', 'type', 'STRING', 'label', '结束日期', 'required', true, 'description', '格式: yyyy-MM-dd HH:mm'),
      JSON_OBJECT('name', 'types', 'type', 'INTEGER', 'label', '请假类型', 'required', true, 'description', '1=事假, 2=病假, 3=年假, 4=调休'),
      JSON_OBJECT('name', 'reason', 'type', 'STRING', 'label', '请假理由', 'required', true, 'description', '请详细说明请假原因'),
      JSON_OBJECT('name', 'check_uids', 'type', 'STRING', 'label', '审批人ID', 'required', true, 'description', '审批人的OA用户ID')
    ),
    'execution', JSON_OBJECT('httpMethod', 'POST', 'contentType', 'application/x-www-form-urlencoded')
  ),
  'API_CALL',
  'oaSystemHandler',
  JSON_OBJECT(
    'type', 'HTTP',
    'http', JSON_OBJECT(
      'url', '/home/leaves/add',
      'method', 'POST',
      'headers', JSON_OBJECT(
        'X-Requested-With', 'XMLHttpRequest',
        'Accept', '*/*'
      )
    )
  ),
  'oa-leave',
  JSON_ARRAY('OA', '请假', 'HR'),
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
-- 3. 验证数据
-- ----------------------------------------------------------------------------

-- 查看已创建的OA请假Action
SELECT
  action_id,
  action_name,
  system_id,
  handler,
  category,
  enabled,
  JSON_PRETTY(interface_binding) as binding_config
FROM `action_registry`
WHERE `system_id` = 'oa-system';

-- 查看binding结构
SELECT
  action_id,
  JSON_EXTRACT(interface_binding, '$.type') as type,
  JSON_EXTRACT(interface_binding, '$.http.url') as endpoint,
  JSON_EXTRACT(interface_binding, '$.http.method') as method
FROM `action_registry`
WHERE `action_id` = 'oa:leave:request';

-- ============================================================================
-- 说明
-- ============================================================================
-- interface_binding 格式说明：
-- {
--   "type": "HTTP",           // 绑定类型
--   "http": {                 // HTTP配置（嵌套对象）
--     "url": "/home/leaves/add",
--     "method": "POST",
--     "headers": {...}
--   }
-- }
--
-- 注意：http字段是嵌套对象，不是平级的endpoint字段！
-- ============================================================================
