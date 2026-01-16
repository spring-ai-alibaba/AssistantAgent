-- ============================================================================
-- Assistant Agent Planning Module - MySQL Schema
-- 与 ActionRegistryEntity 实体类完全匹配
-- ============================================================================

-- ============================================================================
-- Action Registry Table - 动作注册表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `action_registry` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `action_id` VARCHAR(100) NOT NULL COMMENT '动作唯一标识',
    `action_name` VARCHAR(200) NOT NULL COMMENT '动作名称',
    `description` TEXT COMMENT '动作描述',
    `action_type` VARCHAR(50) NOT NULL COMMENT '动作类型: API_CALL, PAGE_NAVIGATION, FORM_PREFILL, WORKFLOW_TRIGGER, MULTI_STEP, INTERNAL_SERVICE, MCP_TOOL',
    `category` VARCHAR(100) COMMENT '分类',
    `tags` JSON COMMENT '标签列表（JSON数组）',
    `keywords` JSON COMMENT '触发关键词（JSON数组）',
    `synonyms` JSON COMMENT '同义词列表（JSON数组）',
    `example_inputs` JSON COMMENT '示例输入（JSON数组）',
    `parameters` JSON COMMENT '参数定义（JSON）',
    `steps` JSON COMMENT '步骤定义（JSON数组，多步骤动作）',
    `state_schema` JSON COMMENT '状态Schema（JSON）',
    `handler` VARCHAR(500) COMMENT '处理器（HTTP URL 或 类名）',
    `interface_binding` JSON COMMENT '接口绑定配置（JSON）',
    `priority` INT DEFAULT 0 COMMENT '优先级（数值越大优先级越高）',
    `timeout_minutes` INT DEFAULT 30 COMMENT '超时时间（分钟）',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用: 1=启用, 0=禁用',
    `required_permissions` JSON COMMENT '权限要求（JSON数组）',
    `metadata` JSON COMMENT '元数据（JSON）',
    `usage_count` BIGINT DEFAULT 0 COMMENT '使用次数',
    `success_rate` DECIMAL(5,2) DEFAULT 0.00 COMMENT '成功率',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator` VARCHAR(100) COMMENT '创建者',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_action_id` (`action_id`),
    KEY `idx_action_name` (`action_name`),
    KEY `idx_category` (`category`),
    KEY `idx_action_type` (`action_type`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作注册表';

-- ============================================================================
-- Execution Plan Table - 执行计划表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `execution_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `plan_id` VARCHAR(64) NOT NULL COMMENT '计划唯一标识',
    `action_id` VARCHAR(100) NOT NULL COMMENT '动作ID',
    `action_name` VARCHAR(200) COMMENT '动作名称',
    `session_id` VARCHAR(100) COMMENT '会话ID',
    `user_id` VARCHAR(100) COMMENT '用户ID',
    `user_input` TEXT COMMENT '用户原始输入',
    `extracted_parameters` JSON COMMENT '提取的参数（JSON）',
    `steps` JSON COMMENT '步骤列表（JSON）',
    `current_step_index` INT DEFAULT 0 COMMENT '当前步骤索引',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, IN_PROGRESS, WAITING_INPUT, COMPLETED, FAILED, CANCELLED',
    `error_message` TEXT COMMENT '错误信息',
    `output` JSON COMMENT '执行输出（JSON）',
    `step_outputs` JSON COMMENT '各步骤输出（JSON）',
    `expire_at` DATETIME COMMENT '过期时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_id` (`plan_id`),
    KEY `idx_action_id` (`action_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行计划表';

-- ============================================================================
-- Execution Step Table - 执行步骤表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `execution_step` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `step_instance_id` VARCHAR(64) NOT NULL COMMENT '步骤实例唯一标识',
    `plan_id` VARCHAR(64) NOT NULL COMMENT '所属计划ID',
    `step_id` VARCHAR(100) NOT NULL COMMENT '步骤定义ID',
    `name` VARCHAR(200) COMMENT '步骤名称',
    `type` VARCHAR(50) COMMENT '步骤类型',
    `order_num` INT DEFAULT 0 COMMENT '执行顺序',
    `input_values` JSON COMMENT '输入参数值（JSON）',
    `output_values` JSON COMMENT '输出结果（JSON）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, IN_PROGRESS, WAITING_INPUT, COMPLETED, FAILED, SKIPPED',
    `error_message` TEXT COMMENT '错误信息',
    `started_at` DATETIME COMMENT '开始时间',
    `completed_at` DATETIME COMMENT '完成时间',
    `duration_ms` BIGINT COMMENT '执行耗时（毫秒）',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_step_instance_id` (`step_instance_id`),
    KEY `idx_plan_id` (`plan_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行步骤表';

-- ============================================================================
-- Sample Data - 示例数据
-- ============================================================================
INSERT INTO `action_registry` (`action_id`, `action_name`, `description`, `action_type`, `category`, `keywords`, `synonyms`, `parameters`, `handler`, `enabled`, `priority`) VALUES
('erp:product-unit:create', '添加产品单位', '在ERP系统中创建新的产品计量单位，如：个、台、箱、件等', 'API_CALL', 'erp-basic',
 '["添加单位", "新建单位", "创建单位", "新增计量单位", "加单位"]',
 '["加个单位", "建个单位", "录入单位"]',
 '{"execution": {"headers": {"tenant-id": "1"}, "httpMethod": "POST", "contentType": "application/json"}, "parameters": [{"name": "name", "type": "STRING", "label": "单位名称", "required": true, "description": "计量单位名称"}, {"name": "status", "type": "ENUM", "label": "单位状态", "required": false, "description": "单位状态", "defaultValue": "0", "enum": [{"label": "启用", "value": "0"}, {"label": "禁用", "value": "1"}]}]}',
 'https://api.example.com/erp/product-unit/create',
 1, 10),

('erp:product:create', '创建产品', '在系统中创建新的产品，需要指定产品名称、分类、单位和价格', 'API_CALL', '产品管理',
 '["产品", "创建", "新建", "添加", "录入"]',
 '["新增产品", "添加产品", "录入产品", "新建产品"]',
 '[{"name": "name", "type": "STRING", "label": "产品名称", "required": true, "description": "产品的名称"}, {"name": "categoryId", "type": "FOREIGN_KEY", "label": "产品分类", "required": true, "description": "产品所属的分类ID"}, {"name": "unitId", "type": "FOREIGN_KEY", "label": "计量单位", "required": true, "description": "产品的计量单位ID"}, {"name": "price", "type": "NUMBER", "label": "价格", "required": true, "description": "产品的销售价格"}]',
 'https://api.example.com/erp/product/create',
 1, 10)
ON DUPLICATE KEY UPDATE `action_name` = VALUES(`action_name`), `update_time` = CURRENT_TIMESTAMP;
