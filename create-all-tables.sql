-- ============================================================================
-- Trigger Module - MySQL Schema
-- ============================================================================
-- This schema defines the tables for storing trigger definitions and execution logs
-- ============================================================================

-- Trigger Definitions Table
CREATE TABLE IF NOT EXISTS `trigger_definitions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `trigger_id` VARCHAR(100) NOT NULL UNIQUE COMMENT '触发器唯一标识',
    `name` VARCHAR(200) COMMENT '触发器名称',
    `description` VARCHAR(500) COMMENT '触发器描述',
    `source_type` VARCHAR(50) NOT NULL COMMENT '来源类型: AGENT, USER, SYSTEM',
    `source_id` VARCHAR(100) COMMENT '来源标识',
    `created_by` VARCHAR(100) COMMENT '创建者标识',
    `event_protocol` VARCHAR(50) COMMENT '事件协议: time, callback, mq, http_poll',
    `event_key` VARCHAR(200) COMMENT '事件标识',
    `schedule_mode` VARCHAR(50) COMMENT '调度模式: CRON, ONCE, DELAY, INTERVAL',
    `schedule_value` VARCHAR(200) COMMENT '调度值 (Cron表达式、延迟时间等)',
    `condition_function` TEXT COMMENT '条件函数代码',
    `execute_function` TEXT COMMENT '执行函数代码',
    `parameters` JSON COMMENT '执行参数',
    `session_snapshot_id` VARCHAR(100) COMMENT '会话快照ID',
    `graph_name` VARCHAR(100) COMMENT '绑定的图名称',
    `agent_name` VARCHAR(100) COMMENT '绑定的Agent名称',
    `metadata` JSON COMMENT '扩展元数据',
    `status` VARCHAR(20) NOT NULL COMMENT '触发器状态: PENDING_ACTIVATE, ACTIVE, PAUSED, COMPLETED, FAILED, CANCELLED',
    `expire_at` DATETIME COMMENT '过期时间',
    `max_retries` INT COMMENT '最大重试次数',
    `retry_delay` BIGINT COMMENT '重试延迟(毫秒)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_trigger_id` (`trigger_id`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_status` (`status`),
    KEY `idx_schedule_mode` (`schedule_mode`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发器定义表';

-- Trigger Execution Logs Table
CREATE TABLE IF NOT EXISTS `trigger_execution_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `execution_id` VARCHAR(100) NOT NULL UNIQUE COMMENT '执行记录唯一标识',
    `trigger_id` VARCHAR(100) NOT NULL COMMENT '所属触发器ID',
    `scheduled_time` DATETIME COMMENT '预期执行时间',
    `start_time` DATETIME COMMENT '实际开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `status` VARCHAR(20) NOT NULL COMMENT '执行状态: PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT, CANCELLED',
    `error_message` TEXT COMMENT '错误信息',
    `error_stack` TEXT COMMENT '错误堆栈',
    `output_summary` JSON COMMENT '输出摘要',
    `backend_task_id` VARCHAR(100) COMMENT '后端任务ID',
    `thread_id` VARCHAR(100) COMMENT '执行线程ID',
    `sandbox_id` VARCHAR(100) COMMENT '沙箱ID',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_trigger_id` (`trigger_id`),
    KEY `idx_status` (`status`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_scheduled_time` (`scheduled_time`),
    CONSTRAINT `fk_trigger_id` FOREIGN KEY (`trigger_id`) REFERENCES `trigger_definitions`(`trigger_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='触发器执行日志表';

-- Index for efficient query by trigger and time
CREATE INDEX `idx_trigger_start_time` ON `trigger_execution_logs`(`trigger_id`, `start_time` DESC);
-- ============================================================================
-- Learning Module - MySQL Schema
-- ============================================================================
-- This schema defines the table for storing learning records
-- Uses JSON to support generic record storage
-- ============================================================================

-- Learning Records Table
CREATE TABLE IF NOT EXISTS `learning_records` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `namespace` VARCHAR(100) NOT NULL COMMENT '命名空间',
    `record_key` VARCHAR(255) NOT NULL COMMENT '记录键（唯一标识）',
    `record_type` VARCHAR(100) NOT NULL COMMENT '记录类型（Java类名）',
    `record_data` JSON NOT NULL COMMENT '记录数据（序列化为JSON）',
    `learning_type` VARCHAR(50) COMMENT '学习类型',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_namespace_key` (`namespace`, `record_key`),
    KEY `idx_namespace` (`namespace`),
    KEY `idx_record_type` (`record_type`),
    KEY `idx_learning_type` (`learning_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习记录表';

-- 注意：
-- 1. record_data 使用 JSON 类型存储泛型学习记录
-- 2. record_type 存储 Java 类型全名，用于反序列化
-- 3. UNIQUE KEY (namespace, record_key) 确保同一命名空间下的键唯一
-- 4. 索引优化查询性能
