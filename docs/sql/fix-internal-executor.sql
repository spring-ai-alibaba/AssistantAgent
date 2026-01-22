-- ============================================================================
-- 快速修复：No executor found for step type: INTERNAL_SERVICE
-- ============================================================================

-- 问题：InternalExecutor.getExecutorType() 返回 "INTERNAL"
-- 但SQL中配置的是 "INTERNAL_SERVICE"
--
-- 解决：更新数据库，将 type 改为 "INTERNAL"

-- ----------------------------------------------------------------------------
-- 1. 删除旧数据
-- ----------------------------------------------------------------------------
DELETE FROM action_registry WHERE action_id = 'oa:leave:request';

-- ----------------------------------------------------------------------------
-- 2. 插入正确配置
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
  'INTERNAL',                  -- ← 关键：必须是INTERNAL（不是INTERNAL_SERVICE）
  'oaSystemActionService',
  JSON_OBJECT(
    'type', 'INTERNAL',
    'internal', JSON_OBJECT(
      'beanName', 'oaSystemActionService',
      'methodName', 'execute',
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
-- 3. 验证配置
-- ----------------------------------------------------------------------------
SELECT
  action_id,
  action_type,
  handler,
  JSON_EXTRACT(interface_binding, '$.type') as binding_type,
  JSON_EXTRACT(interface_binding, '$.internal.beanName') as bean_name
FROM action_registry
WHERE action_id = 'oa:leave:request';

-- 预期结果：
-- action_id           | action_type | handler                    | binding_type | bean_name
-- --------------------|-------------|----------------------------|--------------|---------------------
-- oa:leave:request  | INTERNAL    | oaSystemActionService  | INTERNAL      | oaSystemActionService

-- ============================================================================
-- 说明
-- ============================================================================
-- InternalExecutor.getExecutorType() 返回 "INTERNAL"
-- 数据库中配置的 action_type 必须是 "INTERNAL" 才能匹配
--
-- 执行流程：
-- UnifiedIntentRecognitionHook
--   → DefaultPlanGenerator
--   → DefaultPlanExecutor
--   → InternalExecutor（通过action_type="INTERNAL"匹配）✅
--     → 调用 oaSystemActionService.execute(params)
--       → OaSystemHandler.execute(actionId, params, context)
--         → OA API调用
-- ============================================================================
