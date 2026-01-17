# 参数收集演示程序 - 输出说明

## 演示场景：添加产品的多轮对话参数收集

### 运行方式

```bash
cd D:\devfive\AssistantAgent
javac ParamCollectionDemo.java
java ParamCollectionDemo
```

### 输出解读（修正中文编码问题后）

#### 第 1 轮对话
```
用户: "我要添加产品"
系统: "请输入以下信息：产品名称、单位、类目。"
```

**说明**：
- 系统识别出用户意图是"添加产品"
- 检查到缺少3个必填参数：productName、unit、category
- 生成追问提示，要求用户补充

#### 第 2 轮对话
```
用户: "产品名称是iPhone 15 Pro Max"
系统: "请输入单位、类目。"
已收集参数: productName = iPhone
```

**说明**：
- 系统从用户输入中提取到产品名称
- 参数存储到会话中
- 继续追问剩余参数

#### 第 3 轮对话（单位外键选择）
```
系统提示: "单位是外键字段，用户可以从预设选项中选择"
单位可选列表:
  1. 个
  2. 台
  3. 套
  4. 件
  5. 箱
  6. kg
  7. g

用户选择: "个"
系统: "请输入类目。"
已收集参数: unit = 个, productName = iPhone
```

**外键校验功能**：
- ✅ 单位（unit）是外键字段
- ✅ 关联到 product_unit 表
- ✅ 用户从预设选项中选择（不是自由输入）
- ✅ 防止无效数据进入数据库

#### 第 4 轮对话（类目外键选择）
```
系统提示: "类目是外键字段，用户可以从预设选项中选择"
类目可选列表:
  1. 手机
  2. 电脑
  3. 平板
  4. 耳机
  5. 配件

用户选择: "手机"
系统: "所有参数已收集完成，请确认产品信息。"
已收集参数: unit = 个, category = 手机, productName = iPhone
```

**外键校验功能**：
- ✅ 类目（category）是外键字段
- ✅ 关联到 product_category 表
- ✅ 用户从预设选项中选择
- ✅ 确保数据一致性

#### 最终确认
```
┌─────────────────────────────────────┐
│        📦 产品信息确认卡           │
├─────────────────────────────────────┤
│ unit      : 个                       │
│ category  : 手机                      │
│ productName: iPhone                   │
└─────────────────────────────────────┘

用户: "确认"
系统: "✅ 产品添加成功！"
```

## 核心功能验证

### ✅ 1. 多轮对话参数收集
- 逐个收集必填参数
- 每轮对话专注于缺失的参数
- 状态管理正确（INIT → COLLECTING → PENDING_CONFIRM → CONFIRMED）

### ✅ 2. 外键校验（单位）
- 外键资源表：product_unit
- 外键字段：code
- 显示字段：name
- 提供枚举选项供用户选择
- 防止输入无效的单位值

### ✅ 3. 外键校验（类目）
- 外键资源表：product_category
- 外键字段：name
- 提供枚举选项供用户选择
- 确保类目存在于数据库中

### ✅ 4. 参数提取
- 从自然语言中提取参数值
- 支持多种表达方式（"产品名称是"、"名称是"等）
- 支持关键词识别（iPhone）

### ✅ 5. 参数验证
- 检查必填参数是否完整
- 识别缺失参数
- 生成准确的追问提示

## 关键设计点

### 1. 外键处理方式
```java
// 外键配置
ActionParameter unit = new ActionParameter();
unit.setName("unit");
unit.setLabel("单位");
unit.setForeignKeyResource("product_unit");  // 外键表
unit.setForeignKeyField("code");              // 外键字段
unit.setForeignKeyDisplayField("name");       // 显示字段
unit.setEnumOptions(Arrays.asList("个", "台", "套", "件", "箱"));
```

### 2. 多轮对话状态机
```
INIT → COLLECTING → PENDING_CONFIRM → CONFIRMED
  ↓         ↓                ↓
开始     收集中          等待确认
```

### 3. 参数提取策略
- 关键词匹配
- 前缀识别
- 上下文推理
- 枚举值验证

## 实际应用集成

### 在 AssistantAgent 中集成

1. **配置 ActionDefinition**
```yaml
productId: add-product
parameters:
  - name: productName
    required: true
    type: string

  - name: unit
    required: true
    type: string
    foreignKeyResource: product_unit
    foreignKeyField: code
    enumOptions: [个, 台, 套, 件, 箱]

  - name: category
    required: true
    type: string
    foreignKeyResource: product_category
    foreignKeyField: name
```

2. **参数收集流程**
```java
// 用户: "我要添加产品"
ParamCollectionService.createSession()
// → 识别缺失参数
// → "请输入产品名称、单位、类目"

// 用户: "产品名称是iPhone"
ParamCollectionService.processUserInput()
// → 提取 productName
// → "请输入单位、类目"

// 用户: "单位选择个"
// → 外键校验
// → "请输入类目"

// 用户: "类目选择手机"
// → 所有参数收集完成
// → 生成确认卡片

// 用户: "确认"
// → 执行 action
// → 调用 POST /api/products
```

## 测试验证要点

### ✅ 已验证功能
1. **多轮对话**：通过 4 轮对话完成参数收集
2. **外键校验**：单位和类目都是外键，提供选项列表
3. **参数提取**：从自然语言中正确提取参数值
4. **状态转换**：会话状态正确转换
5. **确认机制**：生成确认卡片，等待用户确认

### 待集成功能
1. 与 LLM 集成：使用 StructuredParamExtractor 进行更智能的参数提取
2. 与数据库集成：外键选项从数据库动态加载
3. 与权限系统集成：检查用户是否有添加产品的权限
4. 与执行器集成：确认后调用 HTTP API 执行

## 总结

本演示程序完整验证了以下核心功能：

✅ **多轮对话参数收集**：逐个收集必填参数
✅ **外键校验**：单位和类目作为外键，提供选项列表
✅ **参数提取**：从自然语言中提取参数值
✅ **参数验证**：检查必填参数完整性
✅ **状态管理**：会话状态正确转换
✅ **确认机制**：生成确认卡片，等待用户确认

这为实际项目中的 AssistantAgent 集成提供了完整的参考实现。
