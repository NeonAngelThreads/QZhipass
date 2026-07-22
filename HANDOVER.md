# 企智通（Qintelipass）后端交接文档

> **致接手此项目的 AI / 开发者**：本文档说明项目的核心业务规则、API 接口和不可破坏的验收标准。在适配前端显示需求时，请自由修改后端代码，但务必保持以下核心功能正常工作。

---

## 项目概览

- **项目名**: 企智通 Qintelipass（Enterprise AI Orchestration 平台）
- **技术栈**: Spring Boot 4.1.0 + Java 21 + MySQL + Redis + Spring Data JPA
- **端口**: `7511`
- **包名**: `org.microsoft.qintelipass`
- **分支**: `feature/update`
- **仓库**: `git@github.com:Linson1129/QZhipass.git`

---

## 当前状态

- 前端代码已从此仓库移除，前端由其他团队负责开发。
- 后端代码完整，可独立运行，以下所有 API 均为已验证的功能。

---

## 数据结构

### users 表（JPA 自动建表，`ddl-auto=update`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (自增) | 主键 |
| name | VARCHAR(50) | 用户姓名 |
| phone | VARCHAR(20), UNIQUE | 手机号（登录账号）|
| password | VARCHAR(200) | BCrypt 加密密码 |
| department | VARCHAR(100) | 部门 |
| email | VARCHAR(100) | 邮箱 |
| wechat | VARCHAR(50) | 微信号 / OpenID |
| status | ENUM | NORMAL / FROZEN / CANCELLED |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| cancelled_at | DATETIME | 注销时间 |
| restored | BOOLEAN | 是否为恢复用户 |

---

## API 端点

### 1. 登录 - `POST /api/v1/portal/login`

**不经过拦截器**（在排除列表中）。

```json
// 请求 - 短信验证码登录
{
  "loginType": "mobile",
  "params": { "phone": "13800138000", "code": "123456" }
}

// 请求 - 密码登录
{
  "loginType": "password",
  "params": { "phone": "13800138000", "password": "mypassword" }
}

// 请求 - 微信登录
{
  "loginType": "wechatLogin",
  "params": { "wechat_openid": "openid_xxx" }
}
```

```json
// 成功响应
{
  "success": true,
  "message": "Login Successful.",
  "data": { "id": "1", "name": "张三", "phone": "13800138000", "status": "NORMAL" }
}

// 已注销用户登录
{ "success": false, "message": "Your account has been cancelled" }

// 冻结用户密码登录
{ "success": false, "message": "Your account has been frozen" }
```

**支持三种登录策略（策略模式，通过 `LoginStrategyFactory` 自动路由）：**
- `mobile` → `MobileCodeLoginStrategy`
- `password` → `PasswordLoginStrategy`
- `wechatLogin` → `WechatLoginStrategy`

### 2. 管理员用户列表 - `GET /api/admin/users`

**不经过拦截器**。需要头 `X-Admin-Key: admin-secret-key`。

支持参数：`q`（搜索姓名/手机号）、`page`（页码，从0开始）、`size`（每页条数）。

```json
// 响应
{
  "success": true,
  "data": {
    "total": 6,
    "items": [
      { "id": 1, "name": "张三", "phone": "13800138000", "wechat": "openid_001", "status": "NORMAL" },
      ...
    ]
  }
}
```

### 3. 注销用户 - `DELETE /api/admin/users/{userId}`

已注销用户不可重复注销（幂等）。

### 4. 用户资料 - `GET /api/user/profile`

**受拦截器保护**。需要请求头 `X-User-Id`。已注销用户请求此接口返回 403。

```json
// 成功
{ "success": true, "data": { "id": 1, "name": "张三", "phone": "13800138000", "status": "NORMAL" } }

// 已注销用户
HTTP 403
{ "success": false, "message": "Your account has been cancelled", "code": "USER_CANCELLED" }
```

### 5. 管理员看用户资料 - `GET /api/admin/user/profile`

**受拦截器保护**。

---

## 🔴 不可破坏的验收标准（7条）

以下功能必须完全正常工作，不能因任何代码修改而破坏：

### 1. Admin 可以查看用户列表（含状态）
`GET /api/admin/users` 必须返回所有用户及其 `NORMAL` / `FROZEN` / `CANCELLED` 状态。

### 2. Admin 可以停用（注销）用户
`DELETE /api/admin/users/{userId}` 必须能将用户状态改为 `CANCELLED` 并记录 `cancelledAt`。

### 3. 已注销用户无法通过手机验证码登录
`POST /api/v1/portal/login` 的 `mobile` 类型必须拦截 `CANCELLED` 用户，返回 `"Your account has been cancelled"`。

### 4. 已注销用户无法通过微信登录
`POST /api/v1/portal/login` 的 `wechatLogin` 类型必须拦截 `CANCELLED` 用户。

### 5. 已注销用户的所有 API 请求被拦截
`UserStatusInterceptor` 对 `/api/**`（除登录/注册/管理员列表外）的请求，若 `X-User-Id` 对应用户已注销，必须返回 **HTTP 403** + `{"code": "USER_CANCELLED"}`。

### 6. 防止敏感信息泄露
数据库密码、API Key 等通过环境变量或 `.env` 文件注入，不硬编码。`.env` 已加入 `.gitignore`。

### 7. API 响应格式保持一致
所有 API 统一使用 `ResponseBody` 格式：
```json
{ "success": true/false, "message": "...", "data": {...} }
```

---

## 🟢 可自由修改的范围

以下内容可以根据前端需求任意调整：

- **CORS 配置**（`WebConfig.java`）：添加/修改允许的 Origin
- **返回字段**：`ResponseBody.data` 中可以增减字段（如添加 `email`、`department`、`avatar` 等）
- **新增 API 端点**：添加注册、修改资料、搜索等功能
- **分页参数**：调整 `page`/`size` 的默认值、起始页
- **登录策略**：新增其他登录方式（如 LDAP、OAuth）
- **密码加密**：修改 BCrypt 强度或加盐策略
- **Redis 缓存**：增加或修改缓存策略
- **包名/类名**：可以重命名（但注意更新所有引用）
- **配置项**：添加或调整 application.properties 中的配置
- **登录参数名**：可调整 `phone`/`phone_number`、`code`/`sms` 等参数映射

---

## 关键代码位置

| 功能 | 文件 |
|------|------|
| 登录入口 | `AuthController.java` |
| 策略工厂 | `LoginStrategyFactory.java` |
| 短信登录 | `logins/MobileCodeLoginStrategy.java` |
| 密码登录 | `logins/PasswordLoginStrategy.java` |
| 微信登录 | `logins/WechatLoginStrategy.java` |
| 状态拦截器 | `interceptors/UserStatusInterceptor.java` |
| 拦截器配置 | `configs/InterceptorConfig.java` |
| 用户服务 | `services/UserService.java` |
| 用户实体 | `models/User.java` |
| 状态枚举 | `enums/UserStatus.java` |
| MySQL 仓库 | `repositories/MySQLUserRepositoryImpl.java` |
| Redis 仓库 | `repositories/RedisUserRepositoryImpl.java` |
| 统一响应 | `response/ResponseBody.java` |
| CORS 配置 | `configs/WebConfig.java` |
| 应用配置 | `resources/application.properties` |

---

## 测试辅助功能

`MobileCodeLoginStrategy` 中有一个测试模式：当 Redis 中没有该手机号的短信验证码时，允许使用 **`123456`** 作为万能验证码登录。此功能用于开发测试，上线前建议移除或通过环境变量开关控制。

---

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_HOST` | localhost | MySQL 主机 |
| `MYSQL_PORT` | 3306 | MySQL 端口 |
| `MYSQL_DB` | qzhipass | 数据库名 |
| `MYSQL_USER` | root | 数据库用户 |
| `MYSQL_PASSWORD` | [必填] | 数据库密码 |
| `REDIS_HOST` | localhost | Redis 主机 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | [可选] | Redis 密码 |
| `ADMIN_SECRET_KEY` | admin-secret-key | 管理员 API Key |

---

## 构建 & 运行

```bash
# 构建
./mvnw clean package -DskipTests

# 运行
java -jar target/qintelipass-0.0.1-SNAPSHOT.jar

# 或指定环境变量
MYSQL_PASSWORD=xxx REDIS_PASSWORD=xxx java -jar target/qintelipass-0.0.1-SNAPSHOT.jar
```

---

**文档版本**: 1.0 | **日期**: 2026-07-15 | **准备者**: QZT 后端团队
