# QZhipass 用户注销功能 - 前端对接文档

## 概述

本文档描述 QZhipass 用户注销功能的前端对接接口规范。

**后端服务地址**: `http://localhost:7510`（本地测试）

---

## 1. 获取用户列表

### 接口信息
- **URL**: `/api/v1/users`
- **Method**: `GET`
- **Description**: 获取所有用户列表及当前状态

### 请求参数
无

### 响应示例

#### 成功响应 (200 OK)
```json
[
  {
    "id": "001",
    "phone": "13800138000",
    "wechatOpenId": "wx123",
    "status": "NORMAL",
    "name": "Test User 1"
  },
  {
    "id": "002",
    "phone": "13800138001",
    "wechatOpenId": "wx456",
    "status": "FROZEN",
    "name": "Test User 2"
  }
]
```

#### 用户状态说明
- `NORMAL` - 正常状态
- `FROZEN` - 冻结状态
- `DEACTIVATED` - 已注销状态

---

## 2. 注销用户

### 接口信息
- **URL**: `/api/v1/users/{userId}/deactivate`
- **Method**: `POST`
- **Description**: 注销指定用户（只能注销 NORMAL 或 FROZEN 状态的用户）

### 路径参数
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | String | 是 | 用户ID |

### 请求示例
```bash
POST /api/v1/users/001/deactivate
```

### 响应示例

#### 成功响应 (200 OK)
```json
{
  "success": true,
  "message": "User deactivated successfully"
}
```

#### 失败响应 (400 Bad Request)
```json
{
  "success": false,
  "message": "Failed to deactivate user"
}
```

**失败原因**:
- 用户不存在
- 用户已经是 DEACTIVATED 状态

---

## 3. 用户登录

### 接口信息
- **URL**: `/api/v1/portal/login`
- **Method**: `POST`
- **Description**: 用户登录（支持手机号+验证码、微信登录）

### 请求参数

#### 手机号登录
```json
{
  "loginType": "smsLogin",
  "params": {
    "phone_number": "13800138000",
    "sms": "123456"
  }
}
```

#### 微信登录
```json
{
  "loginType": "wechatLogin",
  "params": {
    "wechat_openid": "wx123"
  }
}
```

### 响应示例

#### 登录成功 (200 OK)
```json
{
  "success": true,
  "message": "Login Successful."
}
```

#### 登录失败 - 账户已注销 (200 OK，但 success=false)
```json
{
  "success": false,
  "message": "Your account has been deactivated"
}
```

#### 登录失败 - 验证码错误 (400 Bad Request)
```json
{
  "success": false,
  "message": "Wrong smsCode."
}
```

---

## 4. 前端实现要点

### 4.1 管理员用户管理页面

#### 功能需求
1. **显示用户列表**: 调用 `GET /api/v1/users` 获取用户列表
2. **显示用户状态**: 根据 `status` 字段显示不同颜色/文字
   - NORMAL → 绿色："正常"
   - FROZEN → 黄色："已冻结"
   - DEACTIVATED → 红色："已注销"
3. **注销按钮**: 只对 NORMAL 和 FROZEN 状态的用户显示"注销"按钮

#### 注销操作流程
1. 管理员点击"注销"按钮
2. **前端弹出二次确认弹窗**:
   ```
   确认注销
   确定要注销该用户吗？注销后该用户将无法登录。
   [取消]  [确定]
   ```
3. 管理员点击"确定"
4. 前端调用 `POST /api/v1/users/{userId}/deactivate`
5. 根据响应显示成功/失败消息

---

### 4.2 登录页面 - 账户已注销提示

#### 场景1: 用户尝试登录（手机号或微信）
- 后端返回: `{"success": false, "message": "Your account has been deactivated"}`
- **前端处理**:
  - 在登录页面显示错误消息："您的账户已注销"
  - 不建议使用 alert，建议使用页面内提示元素

#### 示例（伪代码）
```javascript
async function handleLogin(loginData) {
  const response = await fetch('/api/v1/portal/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(loginData)
  });
  
  const result = await response.json();
  
  if (result.success) {
    // 登录成功，跳转到首页
    router.push('/home');
  } else {
    // 登录失败
    if (result.message === "Your account has been deactivated") {
      // 显示中文提示
      showErrorMessage("您的账户已注销");
    } else {
      showErrorMessage(result.message);
    }
  }
}
```

---

### 4.3 已登录用户被注销后的处理

#### 场景
用户在浏览器保持登录状态时，管理员在后台注销了该用户的账户。

#### 后端行为
- 用户后续的所有 API 请求都会返回 **403 Forbidden**
- 响应体: `{"success": false, "message": "Your account has been deactivated"}`

#### 前端处理
1. **全局 HTTP 拦截器**: 捕获所有 403 响应
2. **检测特定消息**: 如果 `message` 包含 "deactivated"
3. **弹出提示弹窗**:
   ```
   账户已注销
   您的账户已被管理员注销，即将退出登录。
   [确定]
   ```
4. **点击确定后**:
   - 清除本地存储的登录信息（token、userInfo 等）
   - 跳转到登录页面

#### 示例（Vue.js）
```javascript
// axios 响应拦截器
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response.status === 403) {
      const message = error.response.data.message;
      if (message && message.includes("deactivated")) {
        // 弹出提示
        ElMessageBox.alert('您的账户已被管理员注销，即将退出登录。', '提示', {
          confirmButtonText: '确定',
          callback: () => {
            // 清除登录信息
            store.dispatch('logout');
            router.push('/login');
          }
        });
      }
    }
    return Promise.reject(error);
  }
);
```

---

## 5. 完整流程图

### 5.1 管理员注销用户流程
```
管理员 → 用户管理页面 → 查看用户列表 → 选择用户 → 点击"注销"
                                      ↓
                            弹出二次确认弹窗
                                      ↓
                          管理员点击"确定"
                                      ↓
                    调用 POST /api/v1/users/{userId}/deactivate
                                      ↓
                          后端返回成功/失败
                                      ↓
                            前端显示提示消息
```

### 5.2 已注销用户尝试登录流程
```
用户 → 输入手机号+验证码 → 点击"登录"
                             ↓
                  调用 POST /api/v1/portal/login
                             ↓
                  后端检查用户状态：DEACTIVATED
                             ↓
                  返回 {"success": false, "message": "..."}
                             ↓
                  前端显示："您的账户已注销"
```

### 5.3 已登录用户被注销后的流程
```
用户（已登录） → 在应用中操作
                         ↓
            管理员在后台注销该用户
                         ↓
            用户发起新的 API 请求
                         ↓
                  后端返回 403 + "deactivated" 消息
                         ↓
            前端拦截器捕获 403 响应
                         ↓
            弹出弹窗："您的账户已注销"
                         ↓
            用户点击"确定"
                         ↓
            清除登录信息 → 跳转登录页
```

---

## 6. 注意事项

1. **二次确认弹窗**: 必须在前端实现，后端接口是幂等的（多次调用不会出错）
2. **错误消息国际化**: 后端返回英文消息，前端需转换为中文显示
3. **已登录用户的实时检测**: 建议使用 WebSocket 或轮询机制，实时检测用户状态变化
4. **测试环境**: 使用本地 Redis + H2 数据库，确保 Redis 服务已启动

---

## 7. 测试数据

### 添加测试用户（通过 Redis CLI）
```bash
# 添加用户
redis-cli HSET user:001 phone "13800138000" wechatOpenId "wx123" status "NORMAL" name "Test User 1"

# 添加索引
redis-cli SET user:phone:13800138000 001
redis-cli SET user:wechat:wx123 001
```

### 验证用户状态
```bash
redis-cli HGET user:001 status
# 应返回: DEACTIVATED
```

---

## 8. 联系方式

如有接口对接问题，请联系后端开发人员。

**最后更新**: 2026-06-29
