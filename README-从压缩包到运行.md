# 企智通敏感词告警项目：从压缩包到运行

这份压缩包包含完整的 Spring Boot 后端、Vue 3 前端和新增的敏感词告警管理员页面。

告警页面地址：`http://localhost:5173/admin/alerts`

如果你目前只需要展示告警前端，按照“方式一”操作即可，不需要启动数据库和后端。如果需要运行整个前后端项目，再按照“方式二”操作。

---

## 一、需要提前安装的软件

### 必装

1. **Visual Studio Code**
2. **Node.js 20 LTS 或更高版本**
3. **JDK 21**

安装后打开 PowerShell，分别检查：

```powershell
node -v
npm.cmd -v
java -version
```

应该能够看到 Node.js、npm 和 Java 的版本。Java 必须是 21。

### 推荐的 VS Code 扩展

VS Code 打开项目后，会根据 `.vscode/extensions.json` 推荐以下扩展：

- Vue - Official（Volar）
- Extension Pack for Java
- Spring Boot Extension Pack

点击 VS Code 右下角的推荐提示安装即可。这些扩展不是启动项目的硬性要求，但会提供代码提示、错误检查和后端调试功能。

---

## 二、下载、解压并导入 VS Code

1. 下载 `企智通-敏感词告警完整源码-默认规则可编辑版-2026-07-24.zip`。
2. 在资源管理器中右键压缩包，选择“全部解压”。
3. 推荐解压到路径较短、没有特殊符号的位置，例如：

   ```text
   D:\workspace\qzhipass-alert
   ```

4. 打开 VS Code。
5. 点击“文件”→“打开文件夹”。
6. 选择刚解压的根目录。正确的根目录里应当直接看到：

   ```text
   frontend
   src
   pom.xml
   mvnw.cmd
   README-从压缩包到运行.md
   ```

7. 如果 VS Code 询问“是否信任此文件夹中的作者”，选择“是，我信任此作者”。

不要只打开 `frontend` 目录，否则只能看到前端；打开同时包含 `pom.xml` 和 `frontend` 的根目录，才能管理完整项目。

---

## 三、方式一：只运行告警前端（推荐先这样验证）

告警页面目前带有完整的前端演示数据和交互，所以不启动后端也能查看、筛选、新增规则、打开详情和标记已处理。

### 方法 A：使用终端

在 VS Code 菜单点击“终端”→“新建终端”，依次运行：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

第一次执行 `npm.cmd install` 会从网络下载前端依赖，需要等待一段时间。看到类似以下内容表示成功：

```text
VITE ready
Local: http://localhost:5173/
```

然后打开浏览器访问：

```text
http://localhost:5173/admin/alerts
```

### 方法 B：使用 VS Code 一键任务

1. 按 `Ctrl + Shift + P`。
2. 输入并选择“任务: 运行任务”。
3. 第一次先选择 `1. 前端：安装依赖`。
4. 安装完成后，再次打开“任务: 运行任务”。
5. 选择 `2. 前端：启动告警页面`。
6. 浏览器访问 `http://localhost:5173/admin/alerts`。

### 页面可直接演示的内容

- 左侧“告警”菜单和待处理数量。
- 今日告警、待处理、已通知和启用规则统计。
- 按员工、部门、时间范围和处理状态筛选。
- 查看命中敏感词、具体上下文和通知历史。
- 一键标记已处理，同时将该员工累计次数清零。
- 页面进入后弹出“您有一条新的敏感词通知”。
- 新增、编辑、启停和删除告警规则。
- 系统默认规则初始为“一天内触发 3 次及以上”，管理员也可以修改、启停或删除。

---

## 四、方式二：运行完整前端和后端（H2 开发模式）

项目提供 H2 内存数据库开发配置，因此第一次联调不需要安装 MySQL。Redis 在当前本地配置中也已禁用。

需要同时打开两个 VS Code 终端。

### 终端 1：启动后端

确认当前路径是项目根目录，也就是能看到 `pom.xml` 的目录，然后执行：

```powershell
$env:JWT_SECRET="qzhipass-local-dev-jwt-secret-change-before-production-2026"
$env:AI_API_KEY="你的 DeepSeek API Key"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

如果你暂时不测试 AI 对话，可以先使用占位值让本地配置完整：

```powershell
$env:AI_API_KEY="local-development-placeholder"
```

第一次启动 Maven 会下载 Java 依赖，时间可能较长。看到以下字样表示后端启动成功：

```text
Started QZhipassApplication
```

后端地址：

```text
http://localhost:7510
```

H2 控制台地址：

```text
http://localhost:7510/h2-console
```

H2 控制台连接信息：

```text
JDBC URL: jdbc:h2:mem:qzhipass_dev
User Name: sa
Password: 留空
```

### 终端 2：启动前端

新建第二个终端：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问告警页面：

```text
http://localhost:5173/admin/alerts
```

前端中所有 `/api` 请求会由 Vite 自动代理到 `http://localhost:7510`。

### 使用 VS Code 一键启动前后端

前端依赖安装完成后：

1. 按 `Ctrl + Shift + P`。
2. 选择“任务: 运行任务”。
3. 选择 `完整开发环境：前端 + 后端`。

该任务会同时启动前端和 H2 开发模式后端。任务中使用的是本地演示 JWT 密钥和 AI 占位值，只适用于开发环境，生产环境必须更换。

---

## 五、连接 MySQL 运行后端（可选）

如果要使用长期保存的数据，再安装 MySQL 8，并创建数据库：

```sql
CREATE DATABASE Qzhipass
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

在项目根目录的 PowerShell 中设置本次终端会话的环境变量：

```powershell
$env:DATABASE_URL="jdbc:mysql://localhost:3306/Qzhipass?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DATABASE_USERNAME="root"
$env:DATABASE_PASSWORD="你的 MySQL 密码"
$env:JWT_SECRET="请替换成至少 32 位的随机字符串"
$env:AI_API_KEY="你的 DeepSeek API Key"
.\mvnw.cmd spring-boot:run
```

项目使用 `spring.jpa.hibernate.ddl-auto=update`，会自动创建或更新实体对应的数据表。`docs/sql/new_chat_backend.sql` 是聊天模块的补充 SQL，执行前请先阅读文件说明。

不要把真实密码、JWT 密钥或 API Key 写入源码或提交到 Git。

---

## 六、停止、重新启动和构建

### 停止服务

在对应的 VS Code 终端中按：

```text
Ctrl + C
```

前端和后端分别占用一个终端，需要分别停止。

### 重新启动前端

以后再次打开项目，不需要重复安装依赖，直接运行：

```powershell
cd frontend
npm.cmd run dev
```

只有 `package.json` 或 `package-lock.json` 发生变化时，才需要重新执行 `npm.cmd install`。

### 检查前端能否生产构建

```powershell
cd frontend
npm.cmd run build
```

成功后会生成 `frontend/dist`。这个目录是构建产物，可以删除或重新生成，不需要放进源码压缩包。

### 检查后端编译

在项目根目录运行：

```powershell
.\mvnw.cmd clean package -DskipTests
```

成功后会生成 `target` 目录。

---

## 七、常见问题

### 1. PowerShell 提示“无法加载 npm.ps1，因为禁止运行脚本”

不要使用 `npm`，改用：

```powershell
npm.cmd install
npm.cmd run dev
```

### 2. `npm.cmd install` 下载失败

先确认网络正常，然后在 `frontend` 目录重试。不要从其他项目复制 `node_modules`。

### 3. 提示 Java 版本错误

运行：

```powershell
java -version
.\mvnw.cmd -v
```

两处都应当显示 Java 21。如果不是，需要安装 JDK 21，并将 VS Code 的 Java 运行时切换到 JDK 21。

### 4. 5173 端口被占用

可以临时换端口：

```powershell
cd frontend
npm.cmd run dev -- --port 5174
```

然后访问 `http://localhost:5174/admin/alerts`。

### 5. 7510 端口被占用

先停止之前启动的后端终端，再重新运行。前端代理默认连接 7510，如果更改后端端口，也需要同步修改 `frontend/vite.config.ts` 中的代理地址。

### 6. 告警页面能打开，但其他接口出现 401 或 403

告警页面当前使用前端演示数据，可以独立打开。项目其他管理接口可能要求先登录并取得管理员令牌，这是后端权限控制的正常表现。

### 7. 修改代码后页面没有变化

确认保存了文件，并查看运行 `npm.cmd run dev` 的终端是否有报错。Vite 通常会自动热更新；如果没有，可以刷新浏览器。

---

## 八、告警前端核心文件

```text
frontend/src/views/AlertCenterView.vue       告警主页面、演示数据和全部交互
frontend/src/router/index.ts                 /admin/alerts 路由
frontend/src/views/SensitiveWordsView.vue    管理后台侧栏“告警”入口
frontend/src/styles/global.css               全局样式
frontend/vite.config.ts                      开发服务器和后端代理
```

后端告警接口尚未提供，因此当前告警记录、规则新增和处理操作保存在页面运行内存中，刷新页面会恢复演示数据。后续接入后端时，可将 `AlertCenterView.vue` 中的初始化数据和操作方法替换为 API 请求，页面布局与交互无需重新制作。
