# 添加动作的 Curl 命令

## 1. 添加产品单位动作 (erp:product-unit:create)

```bash
curl -X POST "http://localhost:8080/api/v1/actions" \
  -H "Content-Type: application/json" \
  -d '{
    "actionId": "erp:product-unit:create",
    "actionName": "添加产品单位",
    "description": "在ERP系统中创建新的产品计量单位，如：个、台、箱、件等",
    "actionType": "API_CALL",
    "category": "erp-basic",
    "triggerKeywords": ["添加单位", "新建单位", "创建单位", "新增计量单位", "加单位"],
    "synonyms": ["加个单位", "建个单位", "录入单位"],
    "parameters": [
      {
        "name": "name",
        "type": "STRING",
        "label": "单位名称",
        "examples": ["个", "台", "箱", "件", "套", "瓶", "包"],
        "required": true,
        "description": "计量单位名称"
      },
      {
        "name": "status",
        "type": "ENUM",
        "label": "单位状态",
        "required": false,
        "description": "单位状态",
        "defaultValue": "0",
        "enumValues": ["0", "1"]
      }
    ],
    "handler": "https://api.simplify.devefive.com/admin-api/erp/product-unit/create",
    "interfaceBinding": {
      "type": "HTTP",
      "http": {
        "url": "https://api.simplify.devefive.com/admin-api/erp/product-unit/create",
        "method": "POST",
        "headers": {
          "Content-Type": "application/json",
          "tenant-id": "1"
        }
      }
    },
    "timeoutMinutes": 30,
    "enabled": true,
    "priority": 10,
    "metadata": {
      "security": {
        "riskLevel": "LOW"
      }
    }
  }'
```

## 2. 创建产品动作 (erp:product:create)

```bash
curl -X POST "http://localhost:8080/api/v1/actions" \
  -H "Content-Type: application/json" \
  -d '{
    "actionId": "erp:product:create",
    "actionName": "创建产品",
    "description": "在系统中创建新的产品，需要指定产品名称、分类、单位和价格",
    "actionType": "API_CALL",
    "category": "产品管理",
    "triggerKeywords": ["产品", "创建", "新建", "添加", "录入"],
    "synonyms": ["新增产品", "添加产品", "录入产品", "新建产品"],
    "parameters": [
      {
        "name": "name",
        "type": "STRING",
        "label": "产品名称",
        "examples": ["iPhone 15", "MacBook Pro", "矿泉水"],
        "required": true,
        "minLength": 1,
        "maxLength": 100,
        "description": "产品的名称"
      },
      {
        "name": "categoryId",
        "type": "FOREIGN_KEY",
        "label": "产品分类",
        "required": true,
        "description": "产品所属的分类ID",
        "foreignKey": {
          "entity": "category",
          "field": "id",
          "displayField": "name",
          "validationStepId": "validate-category"
        }
      },
      {
        "name": "unitId",
        "type": "FOREIGN_KEY",
        "label": "计量单位",
        "required": true,
        "description": "产品的计量单位ID",
        "foreignKey": {
          "entity": "unit",
          "field": "id",
          "displayField": "name",
          "validationStepId": "validate-unit"
        }
      },
      {
        "name": "price",
        "type": "NUMBER",
        "label": "价格",
        "examples": ["99.9", "1299", "25.5"],
        "required": true,
        "minValue": 0.01,
        "description": "产品的销售价格"
      },
      {
        "name": "description",
        "type": "STRING",
        "label": "产品描述",
        "required": false,
        "maxLength": 500,
        "description": "产品的详细描述信息"
      }
    ],
    "handler": "https://api.simplify.devefive.com/admin-api/erp/product/create",
    "interfaceBinding": {
      "type": "HTTP",
      "http": {
        "url": "https://api.simplify.devefive.com/admin-api/erp/product/create",
        "method": "POST",
        "headers": {
          "Content-Type": "application/json"
        }
      }
    },
    "timeoutMinutes": 30,
    "enabled": true,
    "priority": 10,
    "metadata": {
      "security": {
        "riskLevel": "MEDIUM",
        "requiredPermissions": ["product:create"]
      },
      "execution": {
        "confirmationPrompt": "确认要创建产品「${name}」吗？分类：${categoryId}，单位：${unitId}，价格：${price}",
        "requireConfirmation": true
      }
    }
  }'
```

## 3. 验证添加成功

```bash
# 列出所有动作
curl -X GET "http://localhost:8080/api/v1/actions"

# 获取特定动作详情
curl -X GET "http://localhost:8080/api/v1/actions/erp:product-unit:create"
curl -X GET "http://localhost:8080/api/v1/actions/erp:product:create"
```

## 4. 测试动作匹配

```bash
# 测试匹配 "添加单位"
curl -X POST "http://localhost:8080/api/v1/actions/match" \
  -H "Content-Type: application/json" \
  -d '{"userInput": "添加一个产品单位", "maxMatches": 5}'

# 测试匹配 "创建产品"
curl -X POST "http://localhost:8080/api/v1/actions/match" \
  -H "Content-Type: application/json" \
  -d '{"userInput": "我想创建一个新产品", "maxMatches": 5}'
```
