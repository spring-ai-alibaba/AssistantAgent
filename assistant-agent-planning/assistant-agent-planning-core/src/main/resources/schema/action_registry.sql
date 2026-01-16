-- Action Registry Table Schema
-- Used by assistant-agent-planning module for storing action definitions

CREATE TABLE IF NOT EXISTS `action_registry` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `action_id` VARCHAR(128) NOT NULL COMMENT '动作唯一标识',
    `action_name` VARCHAR(128) NOT NULL COMMENT '动作名称',
    `description` TEXT COMMENT '动作描述',
    `action_type` VARCHAR(32) COMMENT '动作类型: API_CALL, PAGE_NAVIGATION, FORM_PREFILL, WORKFLOW_TRIGGER, MULTI_STEP, INTERNAL_SERVICE, MCP_TOOL',
    `category` VARCHAR(64) COMMENT '分类',
    `tags` JSON COMMENT '标签列表 JSON数组',
    `trigger_keywords` JSON COMMENT '触发关键词 JSON数组',
    `synonyms` JSON COMMENT '同义词 JSON数组',
    `example_inputs` JSON COMMENT '示例输入 JSON数组',
    `parameters` JSON COMMENT '参数定义 JSON数组',
    `steps` JSON COMMENT '执行步骤定义 JSON数组',
    `state_schema` JSON COMMENT '状态模式 JSON对象',
    `handler` VARCHAR(512) COMMENT '处理器类名或 Bean 名称',
    `interface_binding` JSON COMMENT '接口绑定配置 JSON对象',
    `priority` INT DEFAULT 0 COMMENT '优先级，数值越大优先级越高',
    `timeout_minutes` INT DEFAULT 30 COMMENT '超时时间（分钟）',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用: 0-否, 1-是',
    `required_permissions` JSON COMMENT '所需权限 JSON数组',
    `metadata` JSON COMMENT '元数据 JSON对象',
    `usage_count` BIGINT DEFAULT 0 COMMENT '使用次数统计',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_action_id` (`action_id`),
    UNIQUE KEY `uk_action_name` (`action_name`),
    KEY `idx_category` (`category`),
    KEY `idx_action_type` (`action_type`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作注册表';

-- Example data
INSERT INTO `action_registry` (`action_id`, `action_name`, `description`, `action_type`, `category`, `tags`, `trigger_keywords`, `synonyms`, `example_inputs`, `enabled`, `priority`)
VALUES
    ('action_add_product', '添加产品', '向系统中添加新产品信息', 'API_CALL', 'product', '["product", "crud"]', '["添加产品", "新增产品", "创建产品"]', '["录入产品", "新建产品"]', '["帮我添加一个产品", "我要录入新产品信息"]', 1, 100),
    ('action_query_order', '查询订单', '根据条件查询订单信息', 'API_CALL', 'order', '["order", "query"]', '["查询订单", "订单查询", "查看订单"]', '["搜索订单", "找订单"]', '["帮我查一下订单", "查询今天的订单"]', 1, 90),
    ('action_send_notification', '发送通知', '向用户发送系统通知', 'INTERNAL_SERVICE', 'notification', '["notification", "message"]', '["发送通知", "发送消息"]', '["发消息", "通知用户"]', '["发送一条通知给张三", "通知所有管理员"]', 1, 80);
