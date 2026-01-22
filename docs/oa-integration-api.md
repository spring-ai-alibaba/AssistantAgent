# OA系统集成API接口文档

## 基础信息

- **Base URL**: `http://localhost:8081`
- **Content-Type**: `application/json`
- **字符编码**: `UTF-8`
- **认证方式**: JWT Token (Header: Token)

---

## API接口列表

### 1. 获取用户Token

获取用户的JWT Token，用于后续API调用。

**接口地址**: `POST /api/oa_integration/get_token`

**请求参数**:
```json
{
  "assistant_user_id": "U001"
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| assistant_user_id | string | 是 | AssistantAgent平台用户ID |

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
    "oa_user_id": "1",
    "username": "admin",
    "name": "管理员"
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 响应码：0成功，1失败 |
| msg | string | 响应消息 |
| data.token | string | JWT Token（有效期2小时） |
| data.oa_user_id | string | OA用户ID |
| data.username | string | OA用户名 |
| data.name | string | OA用户姓名 |

**错误响应**:
```json
{
  "code": 1,
  "msg": "未找到绑定用户"
}
```

---

### 2. 获取用户信息

根据OA用户ID获取用户详细信息。

**接口地址**: `GET /api/oa_integration/get_userinfo`

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oa_user_id | string | 是 | OA用户ID（URL参数） |
| Token | string | 是 | JWT Token（请求头） |

**请求示例**:
```bash
GET /api/oa_integration/get_userinfo?oa_user_id=1
Header: Token: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
```

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "1",
    "username": "admin",
    "name": "管理员",
    "email": "admin@example.com",
    "mobile": "13800138000",
    "did": "1",
    "dept_name": "总公司",
    "position_id": "1",
    "position_name": "总经理",
    "thumb": "/uploads/thumb/xxx.jpg"
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | OA用户ID |
| username | string | 登录用户名 |
| name | string | 姓名 |
| email | string | 邮箱 |
| mobile | string | 手机号 |
| did | string | 部门ID |
| dept_name | string | 部门名称 |
| position_id | string | 职位ID |
| position_name | string | 职位名称 |
| thumb | string | 头像URL |

---

### 3. 获取用户权限

获取用户的角色权限和数据权限信息。

**接口地址**: `GET /api/oa_integration/get_permissions`

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oa_user_id | string | 是 | OA用户ID（URL参数） |
| Token | string | 是 | JWT Token（请求头） |

**请求示例**:
```bash
GET /api/oa_integration/get_permissions?oa_user_id=1
Header: Token: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
```

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "user_id": "1",
    "groups": {
      "1": {
        "rules": "1,2,3,4,5,6,7,8,9,10...",
        "title": "超级权限角色"
      }
    },
    "data_auth": {
      "office_admin": {
        "uids": "1,2,3",
        "conf1": "",
        "conf2": "",
        "conf3": ""
      }
    }
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | string | OA用户ID |
| groups | object | 角色组，key为角色ID |
| groups.{id}.rules | string | 权限规则ID列表（逗号分隔） |
| groups.{id}.title | string | 角色名称 |
| data_auth | object | 数据权限配置 |
| data_auth.{name}.uids | string | 有权限的用户ID列表 |
| data_auth.{name}.conf1-3 | string | 其他配置字段 |

---

### 4. 测试接口

测试API是否正常工作。

**接口地址**: `GET /api/oa_integration/test`

**响应示例**:
```json
{
  "code": 0,
  "msg": "OA集成API正常工作",
  "data": {
    "timestamp": 1705824000,
    "date": "2024-01-21 12:00:00"
  }
}
```

---

## 错误码说明

| 错误码 | 说明 |
|-------|------|
| 0 | 成功 |
| 1 | 业务失败（具体错误见msg） |
| 403 | Token签名错误 |
| 404 | 非法请求或Token不能为空 |
| 401 | Token已过期或失效 |

---

## 使用流程

1. **绑定用户**：在`oa_assistant_agent_bind`表中插入绑定关系
2. **获取Token**：调用`get_token`接口获取JWT Token
3. **使用Token**：在后续API请求的Header中携带`Token: xxx`
4. **缓存Token**：Token有效期2小时，建议客户端缓存

---

## 注意事项

1. 所有接口支持跨域访问（CORS）
2. Token有效期为2小时（7200秒），过期需重新获取
3. 绑定关系通过`assistant_user_id`唯一确定
4. 建议生产环境添加IP白名单等安全措施
5. Token应该妥善保管，不要泄露

---

## PHP代码示例

```php
// 获取Token
$ch = curl_init();
curl_setopt($ch, CURLOPT_URL, 'http://localhost:8081/api/oa_integration/get_token');
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode([
    'assistant_user_id' => 'U001'
]));
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
$response = curl_exec($ch);
$result = json_decode($response, true);

// 使用Token获取用户信息
if ($result['code'] == 0) {
    $token = $result['data']['token'];
    $oaUserId = $result['data']['oa_user_id'];

    // 获取用户信息
    $url = "http://localhost:8081/api/oa_integration/get_userinfo?oa_user_id={$oaUserId}";
    $headers = ['Token: ' . $token];

    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
    curl_setopt($ch, CURLOPT_HTTPGET, true);

    $userInfo = curl_exec($ch);
    print_r(json_decode($userInfo, true));
}
curl_close($ch);
```

## Java代码示例

```java
RestTemplate restTemplate = new RestTemplate();

// 获取Token
OaTokenRequest request = new OaTokenRequest();
request.setAssistantUserId("U001");

OaTokenResponse response = restTemplate.postForObject(
    "http://localhost:8081/api/oa_integration/get_token",
    request,
    OaTokenResponse.class
);

// 使用Token
if (response.getCode() == 0) {
    String token = response.getData().getToken();

    HttpHeaders headers = new HttpHeaders();
    headers.set("Token", token);

    HttpEntity<?> entity = new HttpEntity<>(headers);

    OaUserInfoResponse userInfo = restTemplate.exchange(
        "http://localhost:8081/api/oa_integration/get_userinfo?oa_user_id=1",
        HttpMethod.GET,
        entity,
        OaUserInfoResponse.class
    ).getBody();
}
```

---

**文档版本**: 1.0.0
**最后更新**: 2025-01-21
