-- ============================================================================
-- OA请假集成 - 使用OaSystemHandler执行
-- 将action_type改为INTERNAL_SERVICE，通过bean调用
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 清理旧数据
-- ----------------------------------------------------------------------------
DELETE FROM action_registry WHERE action_id = 'oa:leave:request';

-- ----------------------------------------------------------------------------
-- 插入新配置（使用INTERNAL类型 + OaSystemActionService）
-- ----------------------------------------------------------------------------
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
    )
  ),
  'INTERNAL',                  -- ← 使用INTERNAL（与InternalExecutor.getExecutorType()匹配）
  'oaSystemActionService',    -- ← 使用适配Service Bean
  JSON_OBJECT(
    'type', 'INTERNAL',
    'internal', JSON_OBJECT(
      'beanName', 'oaSystemActionService',  -- ← Spring Bean名称
      'methodName', 'execute',               -- ← 方法名
      'methodParams', JSON_ARRAY(
        JSON_OBJECT('name', 'params', 'type', 'java.util.Map')
      )
    )
  ),
  'oa-leave',
  JSON_ARRAY('OA', '请假', 'HR'),
  10,
  1
);

-- ----------------------------------------------------------------------------
-- 验证配置
-- ----------------------------------------------------------------------------
SELECT
  action_id,
  action_type,
  handler,
  JSON_EXTRACT(interface_binding, '$.internal.beanName') as bean,
  JSON_EXTRACT(interface_binding, '$.internal.methodName') as method
FROM action_registry
WHERE action_id = 'oa:leave:request';

-- ============================================================================
-- 说明
-- ============================================================================
-- 1. action_type = INTERNAL_SERVICE
--    → 系统会使用 InternalExecutor 执行
--    → InternalExecutor 会调用 Spring Bean 的方法
--
-- 2. interface_binding.internal 配置
--    → beanName: oaSystemHandler（Spring Bean名称）
--    → methodName: execute（方法名）
--    → methodParams: 方法参数列表
--
-- 3. 执行流程
--    Agent → InternalExecutor → OaSystemHandler.execute()
-- ============================================================================
