# Findings: "添加单位"无法匹配 Action 问题调查

## 调查记录

### 1. 发现内容

#### 1.1 关键文件位置
- **ActionIntentEvaluator**: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/evaluation/ActionIntentEvaluator.java`
- **SemanticActionProvider**: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/internal/SemanticActionProvider.java`
- **ActionEntityConverter**: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/persistence/converter/ActionEntityConverter.java`
- **ActionDefinition**: `assistant-agent-planning/assistant-agent-planning-api/src/main/java/com/alibaba/assistant/agent/planning/model/ActionDefinition.java`
- **ActionRegistryEntity**: `assistant-agent-planning/assistant-agent-planning-core/src/main/java/com/alibaba/assistant/agent/planning/persistence/entity/ActionRegistryEntity.java`

#### 1.2 匹配流程
1. 用户输入 → ActionIntentEvaluator.evaluate()
2. 调用 ActionProvider.matchActions(userInput, context)
3. SemanticActionProvider 执行两种匹配：
   - **向量语义搜索** (60% 权重)
   - **关键词匹配** (40% 权重)
4. 融合两种结果，返回超过阈值(0.5)的匹配

#### 1.3 关键词匹配逻辑 (SemanticActionProvider.java:158-197)
```java
private double computeKeywordScore(String normalizedInput, ActionDefinition action) {
    double score = 0.0;

    // 1. 精确关键词匹配 (score=0.95)
    if (action.getTriggerKeywords() != null) {
        for (String keyword : action.getTriggerKeywords()) {
            if (normalizedInput.contains(keyword.toLowerCase())) {
                score = Math.max(score, 0.95);
            }
        }
    }

    // 2. 同义词匹配 (score=0.85)
    if (action.getSynonyms() != null && score < 0.95) {
        for (String synonym : action.getSynonyms()) {
            if (normalizedInput.contains(synonym.toLowerCase())) {
                score = Math.max(score, 0.85);
            }
        }
    }

    // 3. 示例匹配 (score=similarity*0.8)
    // 4. 名称匹配 (score=0.7)

    return score;
}
```

#### 1.4 日志分析
从 `logs/assistant-agent.log` 发现：
- ✅ 输入 **"添加产品单位"** → 成功匹配到 `erp:product-unit:create` (confidence=0.54, matchType=KEYWORD_FUZZY)
- ❓ 输入 **"添加单位"** → 日志中没有找到相关记录

---

## 代码片段

### 关键代码：关键词匹配逻辑

**SemanticActionProvider.java:162-168**
```java
// 精确关键词匹配
if (action.getTriggerKeywords() != null) {
    for (String keyword : action.getTriggerKeywords()) {
        if (normalizedInput.contains(keyword.toLowerCase())) {
            score = Math.max(score, 0.95);
        }
    }
}
```

### 数据解析：ActionEntityConverter.java:73

```java
.triggerKeywords(parseJsonList(entity.getTriggerKeywords()))
```

**parseJsonList 方法 (169-179行)**:
```java
private List<String> parseJsonList(String json) {
    if (json == null || json.isBlank()) {
        return Collections.emptyList();
    }
    try {
        return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
        logger.warn("ActionEntityConverter#parseJsonList - reason=failed to parse json list, error={}", e.getMessage());
        return Collections.emptyList();
    }
}
```

---

## 匹配逻辑分析

### 当前实现
1. **数据流**:
   - 数据库 `action_registry.keywords` (TEXT/JSON) → ActionRegistryEntity.triggerKeywords
   - ActionEntityConverter.parseJsonList() → List<String>
   - ActionDefinition.triggerKeywords → SemanticActionProvider

2. **匹配算法**:
   - 用户输入转小写: `normalizedInput = userInput.toLowerCase().trim()`
   - 关键词也转小写: `keyword.toLowerCase()`
   - 使用 `String.contains()` 进行子串匹配
   - 精确关键词匹配得分: 0.95

3. **阈值**:
   - 默认匹配阈值: 0.5
   - 语义权重: 0.6
   - 关键词权重: 0.4
   - 最高可能得分: 0.95 * 0.4 = 0.38 (仅关键词匹配)

### 问题分析

#### 可能的原因
1. **数据库字段映射问题**:
   - ActionRegistryEntity.java:86 - 字段名是 `triggerKeywords`
   - 但数据库列名是 `keywords`
   - 使用 `@TableField("keywords")` 注解映射 ✅

2. **JSON 解析失败**:
   - 如果 JSON 格式不正确，parseJsonList 会返回空列表
   - 需要检查日志中是否有 "failed to parse json list" 警告

3. **阈值问题**:
   - 关键词匹配最高得分: 0.95 * 0.4 = 0.38
   - 如果没有语义搜索结果，总分 0.38 < 0.5 阈值 ❌
   - **这是最可能的问题！**

4. **匹配类型判断问题** (SemanticActionProvider.java:223-224):
   ```java
   if (hasKeyword && keywordScores.get(actionId) > 0.9) {
       return ActionMatch.MatchType.KEYWORD_EXACT;
   }
   ```
   - 这里检查的是原始 keywordScore (> 0.9)
   - 但最终 confidence 是融合后的分数 (keywordScore * keywordWeight)

---

## 数据分析

### Action Registry 记录
```sql
INSERT INTO `assistant_agent`.`action_registry` (...) VALUES (
  2,
  'erp:product-unit:create',
  '添加产品单位',
  '在ERP系统中创建新的产品计量单位，如：个、台、箱、件等',
  'API_CALL',
  'erp-basic',
  NULL,
  '["添加单位", "新建单位", "创建单位", "新增计量单位", "加单位"]',  -- keywords
  '["加个单位", "建个单位", "录入单位"]',  -- synonyms
  ...
);
```

### Keywords 字段
```json
["添加单位", "新建单位", "创建单位", "新增计量单位", "加单位"]
```

### Synonyms 字段
```json
["加个单位", "建个单位", "录入单位"]
```

---

## 待确认问题
- [x] keywords 字段是 TEXT 类型还是 JSON 类型？ → TEXT 存储 JSON 字符串
- [x] JSON 数组是如何解析和匹配的？ → Jackson ObjectMapper 解析为 List<String>
- [x] 匹配算法是精确匹配还是模糊匹配？ → String.contains() 子串匹配
- [x] 是否有大小写敏感问题？ → 都转小写，不敏感
- [x] 是否有分词或tokenizer处理？ → 无，直接子串匹配
- [ ] **关键问题**: 阈值配置是多少？默认 0.5
- [ ] **关键问题**: 关键词权重是多少？默认 0.4
- [ ] **关键问题**: 纯关键词匹配最高得分 0.38 < 0.5，无法通过阈值！

---

## 初步结论

**最可能的问题**: 匹配阈值设置不合理

- 纯关键词匹配最高得分: 0.95 * 0.4 = **0.38**
- 默认匹配阈值: **0.5**
- 0.38 < 0.5 → **匹配失败** ❌

**验证方法**:
1. 查看是否有语义搜索结果贡献分数
2. 测试输入"添加单位"，查看日志中的 score
3. 检查 vectorService 是否正常工作

**可能的解决方案**:
1. 降低 matchThreshold (从 0.5 降到 0.3)
2. 提高 keywordWeight (从 0.4 提高到 0.6)
3. 提高关键词匹配基础分 (从 0.95 提高到 1.0)
