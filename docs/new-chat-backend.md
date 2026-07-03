# 新建对话后端说明

## 功能范围

本次实现“新建对话”后端能力：登录成功后自动创建初始空白对话，用户也可以手动创建新对话、查询自己的最近对话、读取对话详情、保存消息、切换模型和修改标题。

首次保存 `ASSISTANT` 消息后，如果标题仍是默认值“新建对话”，后端会根据第一条 `USER` 消息自动生成标题并保存。

## MySQL 表设计

建表脚本位于 `docs/sql/new_chat_backend.sql`，只创建本次新增的模型和对话相关表，不包含 `DROP`、`TRUNCATE` 或无条件 `DELETE`。

- `ai_model_configs`：可用模型配置。
- `conversations`：对话主表。
- `conversation_messages`：对话消息表。

`conversations.user_id` 使用 `BIGINT`，对应正式 MySQL 用户表 `user.id`。该字段不是手机号 `phone`。用户表由其他模块负责，本脚本暂不强制添加用户表外键，避免人工执行 SQL 时受到用户表创建顺序影响；如数据库负责人确认需要外键，可在审阅时追加引用 `user(id)` 的约束。

## 身份流程

登录成功后：

1. 登录策略返回用户表的数字 `id`、`user_id` 或 `userId`。
2. `AuthController` 生成 `access_token`。
3. `AuthTokenService` 把 `accessToken -> userId` 写入 Redis。
4. 对话接口从请求头或 Cookie 读取 `accessToken`，再从 Redis 取出当前用户 id。
5. 所有对话读写都使用 `conversationId + userId` 校验归属。

兼容本地短信登录联调：如果登录策略暂时没有返回用户表 id，后端会把手机号解析成 `Long` 作为兜底 id。正式接 MySQL 用户表后，应由登录策略返回真实用户表 `user.id`。

## 登录响应

`POST /api/v1/portal/login` 成功后会返回：

```json
{
  "success": true,
  "message": "Login Successful.",
  "data": {
    "user_id": 1001,
    "access_token": "generated-token",
    "initialConversationId": 1,
    "conversation": {
      "id": 1,
      "conversationId": 1,
      "title": "新建对话",
      "modelKey": null,
      "status": "ACTIVE",
      "createdAt": "2026-06-30T16:00:00",
      "updatedAt": "2026-06-30T16:00:00",
      "lastMessageAt": "2026-06-30T16:00:00"
    }
  }
}
```

## 接口

### 创建空白对话

`POST /api/v1/conversations`

```json
{
  "modelKey": "gpt4-omni"
}
```

### 登录后补建初始对话

`POST /api/v1/conversations/initial`

请求体可为空，返回结构同创建空白对话。

### 最近对话列表

`GET /api/v1/conversations?limit=20`

只返回当前登录用户自己的对话，按 `lastMessageAt`、`updatedAt` 倒序排列。

### 对话详情

`GET /api/v1/conversations/{conversationId}`

### 保存消息

`POST /api/v1/conversations/{conversationId}/messages`

```json
{
  "role": "USER",
  "content": "帮我总结今天的会议",
  "modelKey": "qwen3"
}
```

`role` 支持 `USER`、`ASSISTANT`、`SYSTEM`。

### 修改对话模型

`PATCH /api/v1/conversations/{conversationId}/model`

```json
{
  "modelKey": "qwen3"
}
```

### 修改标题

`PATCH /api/v1/conversations/{conversationId}/title`

```json
{
  "title": "周会总结"
}
```

## 状态码

- `200`：查询或更新成功。
- `201`：创建对话或保存消息成功。
- `400`：参数错误、空消息、不可用 `modelKey`。
- `401`：缺少 token 或 token 过期。
- `403`：对话不属于当前登录用户。
- `404`：对话不存在。

## 前端配合

- 登录成功后读取 `data.initialConversationId`，进入聊天页。
- 后续请求携带 `Authorization: Bearer {accessToken}`，也支持同源 `access_token` Cookie。
- 模型列表使用 `GET /api/v1/models/available`。

## 本地测试

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

自动化测试使用 H2 的 MySQL 兼容模式，不依赖真实 MySQL、Redis 或真实大模型接口。人工联调真实环境时，请先由数据库负责人审阅并执行 `docs/sql/new_chat_backend.sql`。
