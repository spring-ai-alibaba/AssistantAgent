# Planning 模块 API 测试 - Curl 命令

服务地址: `http://localhost:8080`

## 1. 动作管理 API

### 1.1 列出所有动作
```bash
curl -X GET "http://localhost:8080/api/v1/actions" \
  -H "Content-Type: application/json"
```

### 1.2 获取单个动作详情
```bash
curl -X GET "http://localhost:8080/api/v1/actions/erp:product-unit:create" \
  -H "Content-Type: application/json"
```

### 1.3 搜索动作（关键词）
```bash
curl -X GET "http://localhost:8080/api/v1/actions/search?keyword=产品&limit=10" \
  -H "Content-Type: application/json"
```

### 1.4 获取所有分类
```bash
curl -X GET "http://localhost:8080/api/v1/actions/categories" \
  -H "Content-Type: application/json"
```

---

## 2. 动作匹配 API

### 2.1 匹配动作 - 添加产品单位
```bash
curl -X POST "http://localhost:8080/api/v1/actions/match" \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "添加一个产品单位",
    "maxMatches": 5
  }'
```

### 2.2 匹配动作 - 创建产品
```bash
curl -X POST "http://localhost:8080/api/v1/actions/match" \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "我想创建一个新产品",
    "maxMatches": 5
  }'
```

### 2.3 匹配动作 - 加个单位（同义词测试）
```bash
curl -X POST "http://localhost:8080/api/v1/actions/match" \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "帮我加个单位",
    "maxMatches": 5
  }'
```

---

## 3. 计划执行 API

### 3.1 创建执行计划（不执行）
```bash
curl -X POST "http://localhost:8080/api/v1/plans/create" \
  -H "Content-Type: application/json" \
  -d '{
    "actionId": "erp:product-unit:create",
    "userInput": "添加一个产品单位：箱",
    "parameters": {
      "name": "箱",
      "status": "0"
    },
    "context": {
      "sessionId": "test-session-001",
      "userId": "user-001"
    }
  }'
```

### 3.2 执行计划
```bash
curl -X POST "http://localhost:8080/api/v1/plans/execute" \
  -H "Content-Type: application/json" \
  -d '{
    "actionId": "erp:product-unit:create",
    "userInput": "添加一个产品单位：箱",
    "parameters": {
      "name": "箱",
      "status": "0"
    },
    "context": {
      "sessionId": "test-session-001",
      "userId": "user-001"
    }
  }'
```

### 3.3 智能执行（自动匹配并执行）
```bash
curl -X POST "http://localhost:8080/api/v1/plans/smart-execute" \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "添加一个产品单位叫做套",
    "context": {
      "sessionId": "test-session-002",
      "userId": "user-001"
    }
  }'
```

### 3.4 获取计划详情
```bash
curl -X GET "http://localhost:8080/api/v1/plans/{planId}" \
  -H "Content-Type: application/json"
```

### 3.5 恢复执行计划（多轮对话）
```bash
curl -X POST "http://localhost:8080/api/v1/plans/{planId}/resume" \
  -H "Content-Type: application/json" \
  -d '{
    "selection": "A"
  }'
```

### 3.6 取消计划
```bash
curl -X POST "http://localhost:8080/api/v1/plans/{planId}/cancel" \
  -H "Content-Type: application/json"
```

---

## 4. 创建产品测试（带外键依赖）

### 4.1 匹配创建产品动作
```bash
curl -X POST "http://localhost:8080/api/v1/actions/match" \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "新建一个产品叫iPhone 15，价格999",
    "maxMatches": 5
  }'
```

### 4.2 执行创建产品（带参数）
```bash
curl -X POST "http://localhost:8080/api/v1/plans/execute" \
  -H "Content-Type: application/json" \
  -d '{
    "actionId": "erp:product:create",
    "userInput": "创建产品 iPhone 15",
    "parameters": {
      "name": "iPhone 15",
      "categoryId": 1,
      "unitId": 1,
      "price": 999.00,
      "description": "苹果手机"
    },
    "context": {
      "sessionId": "test-session-003",
      "userId": "user-001"
    }
  }'
```

---

## 5. 向量化同步 API

### 5.1 批量向量化所有动作（需要 ES 配置）
启动时会自动初始化索引并同步向量。

如需手动触发，可以调用更新动作 API，系统会自动重新向量化：
```bash
# 启用/禁用动作会触发重新向量化
curl -X POST "http://localhost:8080/api/v1/actions/erp:product-unit:create/enable" \
  -H "Content-Type: application/json"
```

---

## 6. 完整多轮对话测试流程

### 步骤1：用户输入意图
```bash
curl -X POST "http://localhost:8080/api/v1/actions/match" \
  -H "Content-Type: application/json" \
  -d '{
    "userInput": "我想添加一个新的计量单位",
    "maxMatches": 3
  }'
```

### 步骤2：系统返回匹配结果，用户确认后创建计划
```bash
curl -X POST "http://localhost:8080/api/v1/plans/create" \
  -H "Content-Type: application/json" \
  -d '{
    "actionId": "erp:product-unit:create",
    "userInput": "添加计量单位",
    "context": {
      "sessionId": "multi-turn-001"
    }
  }'
```

### 步骤3：用户提供缺失参数（假设返回的 planId 是 xxx）
```bash
curl -X POST "http://localhost:8080/api/v1/plans/{planId}/resume" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "瓶",
    "status": "0"
  }'
```

---

## 7. 健康检查

### 检查服务状态
```bash
curl -X GET "http://localhost:8080/actuator/health" \
  -H "Content-Type: application/json"
```

---

## 注意事项

1. **数据库配置**: 确保 MySQL 连接正确，表 `action_registry` 存在且有数据
2. **ES 配置**: 如需向量搜索功能，确保 ES 服务可用且安装了 `analysis-icu` 插件
3. **API Key**: 确保设置了 `DASHSCOPE_API_KEY` 环境变量（向量化需要）
4. **端口**: 默认 8080，可在 application.yml 中修改

## 启动命令

```bash
cd assistant-agent-start
mvn spring-boot:run
```

或者：
```bash
export DASHSCOPE_API_KEY=your-api-key
java -jar target/assistant-agent-start-0.1.1.jar
```
