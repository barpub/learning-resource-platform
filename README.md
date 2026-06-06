# 学习资源共享平台

基于 `SpringBoot + Vue` 的前后端分离学习资源共享平台，按论文 `毕业论文-最终版-v2_按skills修改.docx` 的系统设计实现。

## 技术栈

- 后端：SpringBoot 2.7、SpringMVC、MyBatis、MySQL 8、Redis、JWT、Maven
- 前端：Vue 3、Vite、Vue Router、Vuex、Axios、Element Plus

## 本地启动

### 快速演示模式（无需 MySQL 密码）

如果只是先查看系统效果，可以使用 H2 内存数据库启动后端：

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

H2 模式会自动创建表和默认账号，重启后数据会重置。

### MySQL 模式：1. 初始化数据库

```powershell
cd backend
mysql --default-character-set=utf8mb4 -u root -p -e "source E:/bishe/learning-resource-platform/backend/src/main/resources/sql/schema.sql"
mysql --default-character-set=utf8mb4 -u root -p -e "source E:/bishe/learning-resource-platform/backend/src/main/resources/sql/data.sql"
```

默认配置使用 `DB_USERNAME`、`DB_PASSWORD` 和 `JWT_SECRET` 环境变量读取本机账号与密钥。
如果数据库已经存在旧表，请额外执行：

```powershell
mysql --default-character-set=utf8mb4 -u root -p -e "source E:/bishe/learning-resource-platform/backend/src/main/resources/sql/migration-001-file-type-length.sql"
```

### MySQL 模式：2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端地址：`http://localhost:8080`

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

如果 PowerShell 拦截 `npm.ps1`，使用：

```powershell
npm.cmd install
npm.cmd run dev
```

前端地址：`http://localhost:5173`

### 一键启动脚本

项目提供了本地启动和停止脚本。默认使用 MySQL 模式：

```powershell
cd E:\bishe\learning-resource-platform
powershell.exe -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -SkipBuild
```

如果只想使用 H2 内存库演示：

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\start-local.ps1 -SkipBuild -Profile h2
```

停止服务：

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\stop-local.ps1
```

## 默认账号

- 管理员：`admin / admin123456`
- 普通用户：`user / user123456`

## 核心功能

- 用户注册、登录、JWT 鉴权、个人信息管理
- 学习资源上传、浏览、检索、详情、下载
- 分类管理
- 评论与 1-5 星评分
- 收藏和取消收藏
- 管理员用户、资源、分类、评论管理
- FTP 远程资源浏览，在线预览 PDF/DOCX/PPTX/图片/音视频/文本

## FTP 远程浏览

1. 管理员在后台 `/admin` → `FTP 连接` 选项卡配置远程服务器地址、账号、被动/主动模式、编码、起始目录。支持"测试"按钮验证连接。
2. 保存的密码会使用项目内置密钥做 AES 加密写库，列表展示时不回显。
3. 登录用户在导航栏"远程资源"页面 `/ftp` 选择启用的连接，即可按目录浏览远程文件。
4. 预览复用平台的浏览能力：
   - 直接内嵌显示：PDF、图片、视频、音频、文本/Markdown/JSON/CSV
   - 前端渲染：DOCX（docx-preview）、PPTX（JSZip + 文本 / 图片提取）
   - 其他类型退化为仅下载
5. 若已有 MySQL 数据库，需要执行迁移脚本 `backend/src/main/resources/sql/migration-003-ftp-connection.sql` 创建 `ftp_connection` 表。H2 模式无需操作，启动时会自动建表。

## Redis 说明

后端会尝试连接本地 Redis。Redis 不可用时，缓存操作自动降级，不影响数据库主流程。生产环境建议部署 Redis 并保持 `application-prod.yml` 配置正确。
