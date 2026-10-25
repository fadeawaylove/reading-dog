# 阅读狗 · reading-dog

Android 原生 Java 小说阅读器，提供搜索、书架、正文分页、四种翻页动画、段评、离线缓存、全书页码和原文位置续读。

源码位于 [android-replica](android-replica/README.md)，最近的修复为 [翻页收尾点击响应](android-replica/TOUCH-TIMING-FIX.md) 和 [段评气泡布局](android-replica/COMMENT-BUBBLE-ALIGNMENT.md)。

## 构建

在 Windows PowerShell 7 中运行：

```powershell
./android-replica/build-touch.ps1
```

构建脚本默认使用 `C:\Android\Sdk` 的 Android 35 SDK/build-tools，以及 Android Studio JBR。构建会在本地生成调试签名，已有设备若使用原本地签名，需保留其 keystore 才能覆盖安装。

## 测试与本地资料

测试源码位于 `android-replica/tests`，与正式 APK 隔离。测试基于连接的真实 Android 设备；部分协议回归依赖本地保留的原生向量和匿名接口配置，因此未将抓包、会话身份、设备数据库或用户阅读数据纳入仓库。纯交互时序测试可用 `tests/build-timing.ps1` 构建。

第三方 APK、反编译资料、签名文件、构建缓存和历史安装包均保留在本地并由 `.gitignore` 排除。历史验证文档中的本地截图/安装包链接可能仅在原工作区可用。
