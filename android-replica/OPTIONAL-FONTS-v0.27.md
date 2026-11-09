# v0.27 系统字体与可选下载

基于当前 v0.26 工作区继续实现，保留 v0.25/v0.26 的同步修复。默认 Typeface.DEFAULT，不再将 reader-body.ttf / reader-serif.ttf 打入正式 APK。原字体移至 backend/fonts，由独立后端 data/fonts 提供。

## 使用

阅读页 → 设置 → 字号右侧「系统字体」打开字体面板。两套字体需主动下载，下载完成前不可选用；下载成功不会自动改变当前字体，点击「使用」后生效。显示字节数、百分比与进度条；失败显示重试，支持取消、切回系统字体和删除。下载继续由应用级管理器执行，离开设置不持有 Activity。

文件先写临时文件，校验确切字节数及 APK 内固定的 SHA-256，再验证可加载 Typeface 并原子重命名。保存位置为应用 files/reader-fonts，独立于离线正文缓存；启动时复核完整性。中断、错误与校验失败不会产生可选字体；取消/删除使用 generation 隔离迟到结果。

独立下载（无需登录）：

- 黑体：https://reader.fadeaway.ltd/fonts/reader-body.ttf （4,694,156 字节）
- 宋体：https://reader.fadeaway.ltd/fonts/reader-serif.ttf （7,463,484 字节）
- 大小、SHA-256 目录：https://reader.fadeaway.ltd/fonts/catalog.json

字体面板「下载链接」复制对应地址。独立链接可用于单独保存字体；应用只选用在自身面板下载并校验的文件，不增加任意本地字体导入流程。

## 升级与位置

新增 font_family（system/sans/serif），旧 serif 设置只有对应下载文件存在且已通过校验时才继续采用，否则持久化回退 system；其他 SharedPreferences 和书库结构不变。删除当前字体时也回退 system。

排版快照保留 Typeface 与 fontKey；全书页码缓存版本提升并纳入字体 ID/文件哈希。切换/删除通过原 settingChanged 重新排版，沿用章节 ID 和原文字符 anchor，不调用新增的进度上传逻辑。

## 验证（2026-11-09，Asia/Shanghai）

- 正式 APK 185,980 字节，原 v0.26 为 8,017,600 字节，减少约 97.7%。ZIP 目录确认正式 APK 不含任何 TTF；字体夹具仅在隔离测试 APK 内。
- FontChecks：38 项覆盖真实 v0.26→新 APK 覆盖升级、其他设置/书库保留、系统默认、HTTP 失败、流中断、同大小损坏文件、重试、进度、取消竞态、持久保存、离线重载和实际 ReaderActivity 字体切换/删除后的字符位置与同步操作不变。
- 独立 live 测试完成两套字体的 Android HTTPS 下载与完整性校验；本机从独立公开链接完整下载并核对两套文件，二进制与原内置文件一致。
- SyncRegressionChecks 28 项、SyncChecks 19 项通过，保留所有既有同步修复与 612 段正文兼容性检查。
- 后端 21 项检查及 9 项上游恢复检查通过，新增字体路由只允许 catalog.json 和两份固定 TTF，不能读取其他文件。
- 全部顶层 Android Java 测试源码编译通过。历史以原内置字体建立的像素金样本不代表系统字体像素结果；字体功能本轮使用新的 FontChecks 验证，未宣称旧版字形金样本全量通过。
- 原升级签名已核验。发布包 SHA-256：`d679398175832c903d8e6c8f7f32475db29de1cec0524034335b5ebccc1b08e6`。
- v0.27 / code 27 已按私有流程发布；发布脚本从后端重新下载 APK 并核对哈希。发布后的 UpdateChecks 32 项通过，包含实际元数据、安装包下载校验和最新版状态。未创建 GitHub Release 或推送标签。

验证使用隔离 Android 35 模拟器；未连接真实手机，未修改个人生产书架。样式截图保存在忽略的设备测试产物目录。v0.26 升级夹具在禁网模拟器内创建，验证结束清理后才恢复联网。

## 开发验证

```powershell
./android-replica/build-touch.ps1
./android-replica/tests/build-fonts.ps1
adb -s emulator-5556 install -r android-replica/touch-build/yuejie.apk
adb -s emulator-5556 install -r android-replica/tests/fonts-build/checks.apk
adb -s emulator-5556 shell am instrument -w -e reader true com.example.yuejie.fontchecks/com.example.yuejie.FontChecks
```

live=true 启用真实后端下载；upgrade=true 仅用于预先通过 FontUpgradeSeed 在真实 v0.26 APK 写入夹具后再覆盖安装的隔离模拟器，不能对个人数据运行。
