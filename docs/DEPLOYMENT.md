# 部署说明

## 后端

```powershell
cd backend
mvn clean package -DskipTests
java -jar target/learning-resource-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

生产环境需要修改：

- `application-prod.yml` 中的数据库账号和密码
- `jwt.secret`
- 上传目录 `file.upload-dir`
- CORS 域名

## 前端

```powershell
cd frontend
npm.cmd install
npm.cmd run build
```

将 `frontend/dist` 部署到 Nginx 静态目录，并将 `/api` 代理到后端 `8080`。

## Office 文档在线预览

项目不依赖 LibreOffice、OnlyOffice 或第三方预览 API。Office 预览在浏览器端完成：

- `.docx` 使用 `docx-preview` 渲染。
- `.pptx` 使用 `jszip` 读取幻灯片 XML，并做基础文本、图片预览。
- `.doc`、`.ppt` 是旧版二进制格式，暂不支持在线预览，请下载后查看或转换为 `.docx/.pptx`。

这种方案不需要在服务器安装转换器，也不会把文件上传到第三方服务。限制是复杂排版、PPT 动画、母版、SmartArt、复杂图表可能与 Office/WPS 显示不完全一致。
