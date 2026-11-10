# 阅读狗 · reading-dog

Android 原生 Java 小说阅读器，提供搜索、书架、正文分页、四种翻页动画、段评、离线缓存、全书页码和原文位置续读。

Android 源码位于 [android-replica](android-replica/README.md)，独立 Java 后端位于 [backend](backend/README.md)。v0.30 恢复手机直连番茄：搜索、详情、目录、正文、段评和图片均不经过自建后台；`https://reader.fadeaway.ltd` 仅提供书架/进度同步、更新和字体资源。详见 [直连修复与验收](android-replica/DIRECT-RESTORE.md)。

安装包沿用既有签名，支持覆盖升级。首次迁移需手动安装 v0.24，之后从 App「关于阅读狗」检查更新；不再发布新版 GitHub Release。GitHub 仅保留历史版本和源码验证工作流。

书架和阅读位置自动同步，无注册或登录页面；离线仍可阅读与修改书架，联网后补传。自用 APK 携带私有访问令牌，不公开分发。后端部署和私有发布流程见 [发布说明](deploy/README.md)。

## 构建

在 Windows PowerShell 7 中运行：

```powershell
./android-replica/build-touch.ps1
```

构建脚本默认使用 `C:\Android\Sdk` 的 Android 35 SDK/build-tools，以及 Android Studio JBR。构建会在本地生成调试签名，已有设备若使用原本地签名，需保留其 keystore 才能覆盖安装。

## 测试与本地资料

测试源码位于 `android-replica/tests`，与正式 APK 隔离。测试基于连接的真实 Android 设备；部分协议回归依赖本地保留的原生向量和匿名接口配置，因此未将抓包、会话身份、设备数据库或用户阅读数据纳入仓库。纯交互时序测试可用 `tests/build-timing.ps1` 构建。

第三方 APK、反编译资料、签名文件、构建缓存和历史安装包均保留在本地并由 `.gitignore` 排除。历史验证文档中的本地截图/安装包链接可能仅在原工作区可用。
