# Elasticsearch索引创建指南

## 前置要求

- ✅ Elasticsearch 8.x 已安装并运行
- ✅ 可访问地址: `http://localhost:9200`
- ✅ 用户名: `elastic`
- ✅ 密码: `XWuH_rhjfz5ZD+Brrn0D` (请根据实际情况修改)

---

## 1. 安装ICU分析插件（必需）

索引使用了`icu_analyzer`分词器，需要先安装ICU插件。

### Docker方式
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
```

### 非Docker方式
```bash
# 进入ES安装目录
cd /path/to/elasticsearch

# 安装插件
bin/elasticsearch-plugin install analysis-icu

# 重启Elasticsearch服务
systemctl restart elasticsearch
# 或
service elasticsearch restart
```

### 验证插件安装
```bash
curl -X GET "http://localhost:9200/_cat/plugins?v" -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

应该看到输出包含 `analysis-icu`。

---

## 2. 创建experiences索引

### 方法1: 使用curl命令（推荐）

#### 完整命令（从文件读取）
```bash
curl -X PUT "http://localhost:9200/experiences" \
  -H "Content-Type: application/json" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D \
  -d @assistant-agent-extensions/src/main/resources/elasticsearch/experience-index-mapping.json
```

#### 完整命令（直接粘贴JSON）
```bash
curl -X PUT "http://localhost:9200/experiences" \
  -H "Content-Type: application/json" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D \
  -d '{
  "mappings": {
    "properties": {
      "id": {
        "type": "keyword"
      },
      "type": {
        "type": "keyword"
      },
      "title": {
        "type": "text",
        "analyzer": "icu_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      },
      "content": {
        "type": "text",
        "analyzer": "icu_analyzer"
      },
      "scope": {
        "type": "keyword"
      },
      "ownerId": {
        "type": "keyword"
      },
      "projectId": {
        "type": "keyword"
      },
      "repoId": {
        "type": "keyword"
      },
      "language": {
        "type": "keyword"
      },
      "tags": {
        "type": "keyword"
      },
      "createdAt": {
        "type": "date"
      },
      "updatedAt": {
        "type": "date"
      },
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

#### Windows PowerShell命令
```powershell
# PowerShell需要转义引号
$body = Get-Content -Raw assistant-agent-extensions/src/main/resources/elasticsearch/experience-index-mapping.json

Invoke-RestMethod -Method PUT `
  -Uri "http://localhost:9200/experiences" `
  -Headers @{"Content-Type"="application/json"} `
  -Credential (Get-Credential -UserName elastic) `
  -Body $body
```

### 方法2: 使用Kibana Dev Tools

如果您安装了Kibana，可以访问 `http://localhost:5601/app/dev_tools#/console` 并执行：

```json
PUT /experiences
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
```

### 方法3: 使用Postman或其他REST客户端

- **Method**: PUT
- **URL**: `http://localhost:9200/experiences`
- **Auth**: Basic Auth
  - Username: `elastic`
  - Password: `XWuH_rhjfz5ZD+Brrn0D`
- **Headers**:
  - `Content-Type: application/json`
- **Body**: 粘贴上面的JSON

---

## 3. 验证索引创建

### 查看所有索引
```bash
curl -X GET "http://localhost:9200/_cat/indices?v" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

应该看到包含 `experiences` 的行。

### 查看索引详细信息
```bash
curl -X GET "http://localhost:9200/experiences?pretty" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

### 查看索引mapping
```bash
curl -X GET "http://localhost:9200/experiences/_mapping?pretty" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

### 查看索引settings
```bash
curl -X GET "http://localhost:9200/experiences/_settings?pretty" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

### 统计文档数
```bash
curl -X GET "http://localhost:9200/experiences/_count?pretty" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D
```

---

## 4. 成功响应示例

创建成功后，应该看到类似响应：

```json
{
  "acknowledged": true,
  "shards_acknowledged": true,
  "index": "experiences"
}
```

---

## 5. 索引字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | keyword | 经验唯一标识 |
| `type` | keyword | 经验类型（REACT_DECISION/CODE_GENERATION/COMMON_SENSE） |
| `title` | text | 经验标题（全文索引，ICU分词） |
| `content` | text | 经验内容（全文索引，ICU分词） |
| `scope` | keyword | 作用域（USER/PROJECT/ORGANIZATION/GLOBAL） |
| `ownerId` | keyword | 所有者ID |
| `projectId` | keyword | 项目ID |
| `repoId` | keyword | 仓库ID |
| `language` | keyword | 编程语言 |
| `tags` | keyword | 标签数组 |
| `createdAt` | date | 创建时间 |
| `updatedAt` | date | 更新时间 |
| `artifact` | object | 工件对象（不索引，仅存储） |
| `fastIntentConfig` | object | FastIntent配置（不索引，仅存储） |
| `metadata` | object | 元数据（不索引，仅存储） |
| `embedding` | dense_vector | 1024维向量（用于语义搜索） |

---

## 6. 索引配置说明

### Shards和Replicas
- **number_of_shards**: 1 - 单分片（适合中小规模数据）
- **number_of_replicas**: 0 - 无副本（开发环境，生产建议设为1）

### ICU Analyzer
- **用途**: 多语言分词（中文、日文、韩文等）
- **优势**: 比standard analyzer更好的多语言支持
- **tokenizer**: `icu_tokenizer` - Unicode文本分段标准

### Vector Search
- **dims**: 1024 - 向量维度（需与embedding模型一致）
- **similarity**: cosine - 余弦相似度（适合文本向量）
- **index**: true - 启用向量索引（支持快速KNN查询）

---

## 7. 常见问题

### Q1: 创建索引失败，提示 "unknown analyzer [icu_analyzer]"
**原因**: 未安装analysis-icu插件
**解决**: 按照步骤1安装ICU插件并重启ES

### Q2: 索引已存在，如何重建？
```bash
# 删除旧索引
curl -X DELETE "http://localhost:9200/experiences" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D

# 重新创建
curl -X PUT "http://localhost:9200/experiences" \
  -H "Content-Type: application/json" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D \
  -d @assistant-agent-extensions/src/main/resources/elasticsearch/experience-index-mapping.json
```

### Q3: 如何修改已有索引的mapping？
Elasticsearch不支持修改已有字段的mapping，需要：
1. 创建新索引（新mapping）
2. Reindex数据到新索引
3. 删除旧索引
4. 使用alias切换

```bash
# 1. 创建新索引
PUT /experiences_v2 { ... new mapping ... }

# 2. Reindex
POST /_reindex
{
  "source": {"index": "experiences"},
  "dest": {"index": "experiences_v2"}
}

# 3. 删除旧索引
DELETE /experiences

# 4. 创建别名
POST /_aliases
{
  "actions": [
    {"add": {"index": "experiences_v2", "alias": "experiences"}}
  ]
}
```

### Q4: 生产环境建议配置
```json
{
  "settings": {
    "number_of_shards": 3,        // 增加分片数
    "number_of_replicas": 1,      // 添加副本
    "refresh_interval": "5s"      // 降低刷新频率（提升写入性能）
  }
}
```

---

## 8. 测试索引

### 插入测试数据
```bash
curl -X POST "http://localhost:9200/experiences/_doc" \
  -H "Content-Type: application/json" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D \
  -d '{
  "id": "test-exp-001",
  "type": "REACT_DECISION",
  "title": "用户登录处理",
  "content": "当用户提交登录表单时，先验证用户名和密码格式，然后调用认证API",
  "scope": "PROJECT",
  "ownerId": "user-001",
  "projectId": "proj-001",
  "language": "java",
  "tags": ["authentication", "security"],
  "createdAt": "2024-01-16T10:00:00Z",
  "updatedAt": "2024-01-16T10:00:00Z"
}'
```

### 搜索测试数据
```bash
curl -X GET "http://localhost:9200/experiences/_search?pretty" \
  -H "Content-Type: application/json" \
  -u elastic:XWuH_rhjfz5ZD+Brrn0D \
  -d '{
  "query": {
    "multi_match": {
      "query": "登录",
      "fields": ["title^2", "content"]
    }
  }
}'
```

---

## 9. 下一步

索引创建完成后：
1. ✅ 验证索引创建成功
2. ✅ 确认mapping正确
3. ⏭️ 创建MySQL表
4. ⏭️ 启动Spring Boot应用
5. ⏭️ 测试存储功能

---

## 附录：完整JSON Mapping文件

文件位置: `assistant-agent-extensions/src/main/resources/elasticsearch/experience-index-mapping.json`

```json
{
  "mappings": {
    "properties": {
      "id": {
        "type": "keyword"
      },
      "type": {
        "type": "keyword"
      },
      "title": {
        "type": "text",
        "analyzer": "icu_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      },
      "content": {
        "type": "text",
        "analyzer": "icu_analyzer"
      },
      "scope": {
        "type": "keyword"
      },
      "ownerId": {
        "type": "keyword"
      },
      "projectId": {
        "type": "keyword"
      },
      "repoId": {
        "type": "keyword"
      },
      "language": {
        "type": "keyword"
      },
      "tags": {
        "type": "keyword"
      },
      "createdAt": {
        "type": "date"
      },
      "updatedAt": {
        "type": "date"
      },
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
```
