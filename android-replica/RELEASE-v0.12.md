# v0.12 发布校验

- 版本：0.12 / versionCode 12，最低 Android API 24，目标 API 35。
- 安装包：`reading-dog-0.12.apk`。
- SHA-256：`4aa62df20bfd446d6794f2434fbe349ef5c821062f4c28d7c344330985b336bc`。
- 签名证书 SHA-256：`3d036c2956aa9369777eb0966aa61ea6c84870a2c87860cd8e8c020ff8990bb8`，与既有安装一致。
- 最终版本 APK 已在小米 6 覆盖安装，系统报告 versionCode 12 / versionName 0.12；DEX 与通过本轮专项真机检查的安装包完全相同。
- 功能验证见 `PROGRESS-DIRECTORY-UPDATE.md`、`ENTRY-DIRECTORY-UPDATE.md`、`CATALOG-SORT.md`、`PARAGRAPH-COMMENT-NEWLINE-FIX.md`。最后五章专项 10 项、入口刷新回归 8 项、四模式翻页回归通过；前序段评 23 项和目录排序 5 项通过。
- 源码主线不纳入设备数据、研究资料、第三方 APK 或 keystore。已签名发布安装包和校验文件保留在独立 `release-assets/v0.12` 分支，由标签 Actions 构建检查通过后发布。
