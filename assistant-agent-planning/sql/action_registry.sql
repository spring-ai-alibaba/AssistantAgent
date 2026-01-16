-- ============================================================================
-- Action Registry 表结构
-- 与 assistant-management 项目保持一致
-- ============================================================================

CREATE TABLE IF NOT EXISTS `action_registry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `action_id` VARCHAR(100) NOT NULL COMMENT '动作唯一标识',
  `action_name` VARCHAR(200) NOT NULL COMMENT '动作名称',
  `description` TEXT COMMENT '动作描述',
  `keywords` JSON COMMENT '触发关键词（JSON数组）',
  `synonyms` JSON COMMENT '同义词列表（JSON数组）',
  `tags` JSON COMMENT '标签列表（JSON数组）',
  `example_inputs` JSON COMMENT '示例输入（JSON数组）',
  `parameters` JSON COMMENT '参数定义（JSON对象，包含parameters数组和execution配置）',
  `action_type` VARCHAR(50) NOT NULL COMMENT '动作类型: API_CALL, PAGE_NAVIGATION, FORM_PREFILL, WORKFLOW_TRIGGER, MULTI_STEP, INTERNAL_SERVICE, MCP_TOOL',
  `handler` VARCHAR(500) COMMENT '处理器（HTTP URL 或 类名）',
  `interface_binding` JSON COMMENT '接口绑定配置（JSON）',
  `category` VARCHAR(100) COMMENT '分类',
  `steps` JSON COMMENT '步骤定义（JSON数组，多步骤动作）',
  `state_schema` JSON COMMENT '状态Schema（JSON）',
  `timeout_minutes` INT DEFAULT 30 COMMENT '超时时间（分钟）',
  `metadata` JSON COMMENT '元数据（JSON）',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用: 1=启用, 0=禁用',
  `priority` INT DEFAULT 10 COMMENT '优先级（数值越大优先级越高）',
  `required_permissions` JSON COMMENT '权限要求（JSON数组）',
  `usage_count` BIGINT DEFAULT 0 COMMENT '使用次数',
  `success_rate` DECIMAL(5,2) DEFAULT 100.00 COMMENT '成功率',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creator` VARCHAR(100) COMMENT '创建者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_action_id` (`action_id`),
  KEY `idx_category` (`category`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作注册表';

-- ============================================================================
-- 如果表已存在，执行以下 ALTER 语句添加缺失字段
-- ============================================================================
-- ALTER TABLE `action_registry` ADD COLUMN `tags` JSON COMMENT '标签列表（JSON数组）' AFTER `synonyms`;
-- ALTER TABLE `action_registry` ADD COLUMN `example_inputs` JSON COMMENT '示例输入（JSON数组）' AFTER `tags`;
-- ALTER TABLE `action_registry` ADD COLUMN `interface_binding` JSON COMMENT '接口绑定配置（JSON）' AFTER `handler`;
-- ALTER TABLE `action_registry` ADD COLUMN `required_permissions` JSON COMMENT '权限要求（JSON数组）' AFTER `priority`;

-- ============================================================================
-- 示例数据（与你的 assistant-management 项目一致）
-- ============================================================================

-- 添加产品单位
INSERT INTO `action_registry` (`action_id`, `action_name`, `description`, `keywords`, `synonyms`, `parameters`, `action_type`, `handler`, `category`, `priority`, `enabled`) VALUES
('erp:product-unit:create', '添加产品单位', '在ERP系统中创建新的产品计量单位，如：个、台、箱、件等',
 '["添加单位", "新建单位", "创建单位", "新增计量单位", "加单位"]',
 '["加个单位", "建个单位", "录入单位"]',
 '{"execution": {"headers": {"tenant-id": "1"}, "httpMethod": "POST", "contentType": "application/json", "requireConfirmation": false}, "parameters": [{"name": "name", "type": "STRING", "label": "单位名称", "examples": ["个", "台", "箱", "件", "套", "瓶", "包"], "required": true, "description": "计量单位名称"}, {"enum": [{"label": "启用", "value": "0"}, {"label": "禁用", "value": "1"}], "name": "status", "type": "ENUM", "label": "单位状态", "required": false, "description": "单位状态", "defaultValue": "0"}]}',
 'API_CALL',
 'https://api.simplify.devefive.com/admin-api/erp/product-unit/create',
 'erp-basic',
 10, 1)
ON DUPLICATE KEY UPDATE `action_name` = VALUES(`action_name`);

-- 创建产品
INSERT INTO `action_registry` (`action_id`, `action_name`, `description`, `keywords`, `synonyms`, `parameters`, `action_type`, `handler`, `category`, `priority`, `enabled`) VALUES
('erp:product:create', '创建产品', '在系统中创建新的产品，需要指定产品名称、分类、单位和价格',
 '["产品", "创建", "新建", "添加", "录入"]',
 '["新增产品", "添加产品", "录入产品", "新建产品"]',
 '[{"name": "name", "type": "STRING", "label": "产品名称", "examples": ["iPhone 15", "MacBook Pro", "矿泉水"], "required": true, "validation": {"maxLength": 100, "minLength": 1}, "description": "产品的名称"}, {"name": "categoryId", "type": "FOREIGN_KEY", "label": "产品分类", "required": true, "foreignKey": {"valueField": "id", "queryPrompt": "查询所有产品分类，返回id和分类名称name", "displayField": "name", "allowNameInput": true, "cacheTtlSeconds": 300}, "promptHint": "请选择产品分类（如：电子产品、服装、食品等）", "description": "产品所属的分类ID"}, {"name": "unitId", "type": "FOREIGN_KEY", "label": "计量单位", "required": true, "foreignKey": {"valueField": "id", "queryPrompt": "查询所有计量单位，返回id和单位名称name", "displayField": "name", "allowNameInput": true, "cacheTtlSeconds": 300}, "promptHint": "请选择计量单位（如：个、件、箱、公斤等）", "description": "产品的计量单位ID"}, {"name": "price", "type": "NUMBER", "label": "价格", "examples": ["99.9", "1299", "25.5"], "required": true, "validation": {"min": 0.01}, "description": "产品的销售价格"}, {"name": "description", "type": "STRING", "label": "产品描述", "required": false, "validation": {"maxLength": 500}, "description": "产品的详细描述信息"}]',
 'API_CALL',
 'https://api.simplify.devefive.com/admin-api/erp/product/create',
 '产品管理',
 10, 1)
ON DUPLICATE KEY UPDATE `action_name` = VALUES(`action_name`);
