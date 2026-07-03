# 新建对话后端说明

## 功能说明

本次新增“新建对话”后端能力：登录用户可以创建空白对话、查看最近对话、读取对话详情、保存消息、修改对话模型和标题，并获取当前可用模型列表。首次保存 `ASSISTANT` 消息后，如果标题仍是默认值“新建对话”，后端会基于第一条 `USER` 消息生成一个简短标题。

## 数据表

- `ai_model_configs`：可用模型配置，包含 `model_key`、`display_name`、`provider`、`enabled`、`sort_order`。
- `conversations`：对话主表，包含 `user_id`、`title`、`model_key`、`status`、`title_customized`、`created_at`、`updated_at`、`last_message_at`。
- `conversation_messages`：消息表，包含 `conversation_id`、`role`、`content`、`model_key`、`created_at`。

建表脚本位于 `docs/sql/new_chat_backend.sql`。脚本只创建新增表和非敏感 demo 模型数据，不会清空或覆盖已有业务表；执行前请人工审阅数据库名和连接环境。

## 认证

登录成功后，后端生成 `access_token` 并写入 Redis：

- Redis key：`auth:token:{accessToken}`
- Redis value：当前 `user_id`
- 有效期：8 小时

后续接口从以下位置解析 token：

- `Authorization: Bearer {accessToken}`
- `X-Access-Token: {accessToken}`
- `access_token` Cookie

对话接口不会信任请求体中的任意 `userId`。未登录返回 `401`，访问其他用户对话返回 `403`，对话不存在返回 `404`。

## 登录后初始对话

采用第一优先方案：`POST /api/v1/portal/login` 登录成功后直接调用 `ConversationService.createInitialConversation` 创建一条空白对话，并在原有 `success/message` 结构基础上追加：

```json
{
  "success": true,
  "message": "Login Successful.",
  "data": {
    "user_id": "13800138000",
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

如果前端不想依赖登录响应，也可以在登录成功后调用 `POST /api/v1/conversations/initial` 创建初始对话。

## 接口

### 创建空白对话

`POST /api/v1/conversations`

```json
{
  "modelKey": "gpt4-omni"
}
```

返回：

```json
{
  "success": true,
  "message": "Conversation created.",
  "data": {
    "id": 2,
    "conversationId": 2,
    "title": "新建对话",
    "modelKey": "gpt4-omni",
    "status": "ACTIVE",
    "createdAt": "2026-06-30T16:00:00",
    "updatedAt": "2026-06-30T16:00:00",
    "lastMessageAt": "2026-06-30T16:00:00"
  }
}
```

### 登录后创建初始对话

`POST /api/v1/conversations/initial`

请求体为空。返回结构同“创建空白对话”。

### 最近对话列表

`GET /api/v1/conversations?limit=20`

`limit` 默认 20，最大 100。只返回当前用户自己的对话，并按 `lastMessageAt`、`updatedAt` 倒序排列。

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": 2,
      "conversationId": 2,
      "title": "新建对话",
      "modelKey": "gpt4-omni",
      "createdAt": "2026-06-30T16:00:00",
      "updatedAt": "2026-06-30T16:00:00",
      "lastMessageAt": "2026-06-30T16:00:00",
      "messageCount": 0
    }
  ]
}
```

### 对话详情

`GET /api/v1/conversations/{conversationId}`

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "conversation": {
      "id": 2,
      "conversationId": 2,
      "title": "预算分析",
      "modelKey": "gpt4-omni",
      "status": "ACTIVE",
      "createdAt": "2026-06-30T16:00:00",
      "updatedAt": "2026-06-30T16:01:00",
      "lastMessageAt": "2026-06-30T16:01:00"
    },
    "messages": [
      {
        "id": 10,
        "conversationId": 2,
        "role": "USER",
        "content": "帮我分析预算",
        "modelKey": "gpt4-omni",
        "createdAt": "2026-06-30T16:00:30"
      }
    ],
    "model": {
      "modelKey": "gpt4-omni",
      "displayName": "GPT-4 Omni",
      "provider": "OPENAI"
    }
  }
}
```

### 保存消息

`POST /api/v1/conversations/{conversationId}/messages`

```json
{
  "role": "USER",
  "content": "帮我分析预算",
  "modelKey": "gpt4-omni"
}
```

`role` 支持 `USER`、`ASSISTANT`、`SYSTEM`。`content` 不能为空，最大 20000 字符。

### 修改对话模型

`PATCH /api/v1/conversations/{conversationId}/model`

```json
{
  "modelKey": "qwen3"
}
```

只能选择 `enabled=true` 的模型。

### 获取可用模型

`GET /api/v1/models/available`

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "modelKey": "gpt4-omni",
      "displayName": "GPT-4 Omni",
      "provider": "OPENAI"
    },
    {
      "modelKey": "gpt4-turbo",
      "displayName": "GPT-4 Turbo",
      "provider": "OPENAI"
    },
    {
      "modelKey": "claude-3.5",
      "displayName": "Claude 3.5 Sonnet",
      "provider": "ANTHROPIC"
    },
    {
      "modelKey": "qwen3",
      "displayName": "千问3",
      "provider": "ALIBABA"
    },
    {
      "modelKey": "deepseek-v4",
      "displayName": "DeepSeek-V4",
      "provider": "DEEPSEEK"
    }
  ]
}
```

前端输入 `#` 时可以调用该接口展示模型选择列表。

### 修改标题

`PATCH /api/v1/conversations/{conversationId}/title`

```json
{
  "title": "预算分析"
}
```

标题不能为空，最大 60 字符。用户主动修改后，后端不会再用首次 AI 回复逻辑覆盖标题。

## 状态码

- `200`：查询或更新成功。
- `201`：创建对话或保存消息成功。
- `400`：参数错误、空消息、不可用 `modelKey`。
- `401`：缺少 token 或 token 过期。
- `403`：对话不属于当前登录用户。
- `404`：对话不存在。

## 标题生成

当前没有接入真实大模型标题总结服务。后端通过 `ConversationTitleGenerator` 接口隔离标题生成能力，默认实现 `LocalConversationTitleGenerator` 是 fallback：清理第一条用户消息中的换行和多余空格，截取最多 60 个字符作为标题。后续接入真实大模型时可以替换该接口实现。

## 前端配合

- 登录成功后可直接读取 `data.initialConversationId`，跳转到未来的对话页，例如 `/chat/{conversationId}` 或前端实际约定路径。
- 如果不使用登录响应里的初始对话，可以登录成功后立刻调用 `POST /api/v1/conversations/initial`。
- 后续请求需要携带 `access_token`，推荐使用 `Authorization: Bearer {accessToken}`；如果同源 Cookie 可用，后端也会读取 `access_token` Cookie。
- 获取模型列表使用 `GET /api/v1/models/available`。

## 本地测试

推荐命令：

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

测试使用 H2 内存数据库，不依赖真实 MySQL、Redis 或真实大模型接口。
