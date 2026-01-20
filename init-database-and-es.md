# Assistant Agent - 数据库和Elasticsearch初始化脚本

## 配置信息

### MySQL配置
- **数据库**: `assistant_agent`
- **用户名**: `root`
- **密码**: `StrongRootPwd`
- **地址**: `localhost:3306`

### Elasticsearch配置
- **地址**: `http://localhost:9200`
- **用户名**: `elastic`
- **密码**: `XWuH_rhjfz5ZD+Brrn0D`
- **索引名**: `experiences`

---

## 一、MySQL数据库初始化

### 1.1 创建数据库

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS `assistant_agent` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE `assistant_agent`;
```

### 1.2 执行完整SQL脚本

**方式1: 使用MySQL命令行**

```bash
# Windows PowerShell
mysql -u root -pStrongRootPwd assistant_agent < "D:\devfive\AssistantAgent\create-all-tables.sql"
mysql -u root -pStrongRootPwd assistant_agent < "D:\devfive\AssistantAgent\assistant-agent-planning\assistant-agent-planning-core\src\main\resources\db\schema-mysql.sql"

# Git Bash
mysql -u root -pStrongRootPwd assistant_agent < D:/devfive/AssistantAgent/create-all-tables.sql
mysql -u root -pStrongRootPwd assistant_agent < D:/devfive/AssistantAgent/assistant-agent-planning/assistant-agent-planning-core/src/main/resources/db/schema-mysql.sql
```

**方式2: 直接复制粘贴SQL到MySQL客户端**

连接到MySQL后，依次执行以下SQL：

```sql
-- ============================================================================
-- 1. Trigger Module Tables (触发器模块)
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

CREATE INDEX `idx_trigger_start_time` ON `trigger_execution_logs`(`trigger_id`, `start_time` DESC);

-- ============================================================================
-- 2. Learning Module Tables (学习模块)
-- ============================================================================

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

-- ============================================================================
-- 3. Planning Module Tables (规划模块)
-- ============================================================================

-- Action Registry Table - 动作注册表
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

-- Execution Plan Table - 执行计划表
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

-- Execution Step Table - 执行步骤表
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
-- 4. Sample Data - 示例数据
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
```

### 1.3 验证数据库初始化

```sql
-- 查看所有表
SHOW TABLES;

-- 应该显示以下表：
-- action_registry
-- execution_plan
-- execution_step
-- learning_records
-- trigger_definitions
-- trigger_execution_logs

-- 查看示例数据
SELECT action_id, action_name FROM action_registry;
```

---

## 二、Elasticsearch索引初始化

### 2.1 前置要求

- Elasticsearch 8.x 已安装并运行
- 已安装 `analysis-icu` 插件

### 2.2 安装ICU分析插件（如未安装）

**Docker环境:**
```bash
# 进入容器
docker exec -it elasticsearch bash

# 安装插件
bin/elasticsearch-plugin install analysis-icu

# 退出容器
exit

# 重启Elasticsearch
docker restart elasticsearch

# 等待30秒让ES完全启动
sleep 30
```

**非Docker环境:**
```bash
# 进入ES安装目录
cd /path/to/elasticsearch

# 安装插件
bin/elasticsearch-plugin install analysis-icu

# 重启ES服务
systemctl restart elasticsearch
```

### 2.3 验证插件安装

```bash
curl -X GET "http://localhost:9200/_cat/plugins?v" -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

应该看到输出包含 `analysis-icu`。

### 2.4 创建experiences索引

**方式1: 使用curl命令 (推荐)**

```bash
# Git Bash / Linux / macOS
curl -X PUT "http://localhost:9200/experiences" \
  -H "Content-Type: application/json" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D \
  -d '{
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "type": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "icu_analyzer",
        "fields": {
          "keyword": {"type": "keyword"}
        }
      },
      "content": {
        "type": "text",
        "analyzer": "icu_analyzer"
      },
      "scope": {"type": "keyword"},
      "ownerId": {"type": "keyword"},
      "projectId": {"type": "keyword"},
      "repoId": {"type": "keyword"},
      "language": {"type": "keyword"},
      "tags": {"type": "keyword"},
      "createdAt": {"type": "date"},
      "updatedAt": {"type": "date"},
      "artifact": {
        "type": "object",
        "enabled": false
      },
      "fastIntentConfig": {
        "type": "object",
        "enabled": false
      },
      "metadata": {
        "type": "object",
        "enabled": false
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1024,
        "index": true,
        "similarity": "cosine"
      }
    }
  },
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "icu_analyzer": {
          "tokenizer": "icu_tokenizer"
        }
      }
    }
  }
}'
```

**方式2: Windows PowerShell**

```powershell
$headers = @{
    "Content-Type" = "application/json"
}

$body = @'
{
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "type": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "icu_analyzer",
        "fields": {
          "keyword": {"type": "keyword"}
        }
      },
      "content": {
        "type": "text",
        "analyzer": "icu_analyzer"
      },
      "scope": {"type": "keyword"},
      "ownerId": {"type": "keyword"},
      "projectId": {"type": "keyword"},
      "repoId": {"type": "keyword"},
      "language": {"type": "keyword"},
      "tags": {"type": "keyword"},
      "createdAt": {"type": "date"},
      "updatedAt": {"type": "date"},
      "artifact": {
        "type": "object",
        "enabled": false
      },
      "fastIntentConfig": {
        "type": "object",
        "enabled": false
      },
      "metadata": {
        "type": "object",
        "enabled": false
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1024,
        "index": true,
        "similarity": "cosine"
      }
    }
  },
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "icu_analyzer": {
          "tokenizer": "icu_tokenizer"
        }
      }
    }
  }
}
'@

$credential = New-Object System.Management.Automation.PSCredential("elastic", (ConvertTo-SecureString "XWuH_rhjfz5ZD+Brrn0D" -AsPlainText -Force))

Invoke-RestMethod -Method PUT -Uri "http://localhost:9200/experiences" -Headers $headers -Credential $credential -Body $body
```

### 2.5 验证索引创建

```bash
# 查看所有索引
curl -X GET "http://localhost:9200/_cat/indices?v" -u elastic:XWuH_rhjfz5ZD+Brrn0D

# 查看索引mapping
curl -X GET "http://localhost:9200/experiences/_mapping?pretty" -u elastic:XWuH_rhjfz5ZD+Brrn0D

# 统计文档数（应该是0）
curl -X GET "http://localhost:9200/experiences/_count?pretty" -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

成功响应示例：
```json
{
  "acknowledged": true,
  "shards_acknowledged": true,
  "index": "experiences"
}
```

---

## 三、快速初始化脚本（一键执行）

### 3.1 创建init.sh（Linux/macOS/Git Bash）

```bash
#!/bin/bash

echo "===== Assistant Agent 数据库和ES初始化 ====="

# MySQL初始化
echo ""
echo "[1/3] 初始化MySQL数据库..."
mysql -u root -pStrongRootPwd -e "CREATE DATABASE IF NOT EXISTS assistant_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -pStrongRootPwd assistant_agent < create-all-tables.sql
mysql -u root -pStrongRootPwd assistant_agent < assistant-agent-planning/assistant-agent-planning-core/src/main/resources/db/schema-mysql.sql
echo "✓ MySQL数据库初始化完成"

# 验证MySQL表
echo ""
echo "[2/3] 验证MySQL表..."
mysql -u root -pStrongRootPwd assistant_agent -e "SHOW TABLES;"

# Elasticsearch初始化
echo ""
echo "[3/3] 创建Elasticsearch索引..."
curl -X PUT "http://localhost:9200/experiences" \
  -H "Content-Type: application/json" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D \
  -d '{
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "type": {"type": "keyword"},
      "title": {"type": "text", "analyzer": "icu_analyzer", "fields": {"keyword": {"type": "keyword"}}},
      "content": {"type": "text", "analyzer": "icu_analyzer"},
      "scope": {"type": "keyword"},
      "ownerId": {"type": "keyword"},
      "projectId": {"type": "keyword"},
      "repoId": {"type": "keyword"},
      "language": {"type": "keyword"},
      "tags": {"type": "keyword"},
      "createdAt": {"type": "date"},
      "updatedAt": {"type": "date"},
      "artifact": {"type": "object", "enabled": false},
      "fastIntentConfig": {"type": "object", "enabled": false},
      "metadata": {"type": "object", "enabled": false},
      "embedding": {"type": "dense_vector", "dims": 1024, "index": true, "similarity": "cosine"}
    }
  },
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {"analyzer": {"icu_analyzer": {"tokenizer": "icu_tokenizer"}}}
  }
}'

echo ""
echo "✓ Elasticsearch索引创建完成"

echo ""
echo "===== 初始化完成 ====="
echo "MySQL数据库: assistant_agent"
echo "ES索引: experiences"
```

### 3.2 执行初始化脚本

```bash
chmod +x init.sh
./init.sh
```

---

## 四、验证和测试

### 4.1 验证MySQL

```sql
-- 连接到数据库
mysql -u root -pStrongRootPwd assistant_agent

-- 查看所有表（应该显示6个表）
SHOW TABLES;

-- 查看示例动作数据
SELECT action_id, action_name FROM action_registry;
```

### 4.2 验证Elasticsearch

```bash
# 查看索引
curl -X GET "http://localhost:9200/_cat/indices?v" -u elastic:XWuH_rhjfz5ZD+Brrn0D

# 验证experiences索引存在
curl -X GET "http://localhost:9200/experiences?pretty" -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

---

## 五、常见问题

### Q1: MySQL连接失败
**解决方案:**
1. 检查MySQL服务是否运行
2. 验证密码是否正确
3. 确认数据库端口3306未被占用

### Q2: Elasticsearch索引创建失败 "unknown analyzer [icu_analyzer]"
**解决方案:**
```bash
# 安装analysis-icu插件并重启ES
docker exec -it elasticsearch bin/elasticsearch-plugin install analysis-icu
docker restart elasticsearch
```

### Q3: 如何重置数据库和索引？
```bash
# 删除MySQL数据库
mysql -u root -pStrongRootPwd -e "DROP DATABASE IF EXISTS assistant_agent;"

# 删除ES索引
curl -X DELETE "http://localhost:9200/experiences" -u elastic:XWuH_rhjfz5ZD+Brrn0D

# 重新执行初始化脚本
./init.sh
```

---

## 六、启动应用

初始化完成后，启动Assistant Agent应用：

```bash
cd assistant-agent-start
mvn spring-boot:run
```

或

```bash
# 构建并运行
mvn clean install -DskipTests
cd assistant-agent-start
java -jar target/assistant-agent-start-0.1.1.jar
```

应用启动后访问: `http://localhost:8080`

---

## 附录：配置文件位置

- **MySQL配置**: `assistant-agent-start/src/main/resources/application.yml` (L110-114)
- **ES配置**: `assistant-agent-start/src/main/resources/application.yml` (L130-137)
- **SQL脚本**:
  - `create-all-tables.sql` (Trigger + Learning)
  - `assistant-agent-planning/assistant-agent-planning-core/src/main/resources/db/schema-mysql.sql` (Planning)
