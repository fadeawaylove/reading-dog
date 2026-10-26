# 检查更新

实现依据：用户认可的 `design/app-update/proposal.html`。入口位于首页搜索栏右侧的关于图标。关于页沿用暖纸色、草绿按钮与当前应用图标，显示实际安装版本，提供项目主页和手动检查更新。

## 行为

- 不自动检查，不在阅读时弹窗。仅使用公开 GitHub Release，不携带账号或访问令牌。
- 从 `https://api.github.com/repos/fadeawaylove/reading-dog/releases/latest` 读取正式版。按数字分段比较版本；拒绝草稿、预发布、异常版本号、缺少 APK 或校验信息的发布。
- 附件遵循现有发布约定：`reading-dog-{version}.apk`，优先使用 GitHub 的 SHA-256 digest，缺少 digest 时读取同一 Release 的 `SHA256SUMS.txt`。
- 下载可取消、失败可重试。离开关于页继续阅读时下载继续，返回可查看状态。应用进程被系统终止时不承诺后台续传；完整 APK 在下次主动检查时重新校验并复用，未完成的下载从头重试。
- 下载限额 100 MiB，检查声明大小、SHA-256、包名、签名、versionName、递增 versionCode 和最低 Android 版本。失败不进入待安装状态。
- 安装包位于应用私有目录；非导出的只读 Provider 仅开放一个 APK URI，通过临时读权限交给系统安装器。不请求存储权限。
- Android 8+ 首次安装需要用户在系统设置允许阅读狗安装应用；返回后仍需点击“立即安装”。应用不会自行修改安装授权或静默安装。
- GitHub 不可达、限流、缺少附件等情形会显示错误与重试入口，不把请求失败当成“已是最新”。

实现：`UpdateRelease.java` 定义发布约定与版本比较；`AppUpdater.java` 管理应用级网络工作和下载状态；`AboutActivity.java` 展示界面；`UpdateApkProvider.java` 提供只读安装包。

## 2026-10-26 验证

- 独立接口验证：电脑和小米 6（Android 9 / API 28）均成功读取公开正式版 v0.13；手机使用生产网络代码跟随 GitHub 附件跳转并下载真实 APK，大小与 SHA-256 均匹配。
- 正式 APK SHA-256：`88734f055799e070a93086c53ab1d6456d10c2cf10a00b6f60b510389f7501f8`。此处指远端 v0.13，不是本次本地功能预览包。
- `build.ps1` 构建通过。修复 Windows D8 参数超长：先将 class 打包为 JAR，再交给 D8，与现有 Linux CI 的方式一致。
- `tests/build-updates.ps1` 构建独立 instrumentation；小米 6 上 28 项检查通过。包括真实最新版查询/下载、数值版本比较、异常发布拒绝、取消后的回调隔离、离线提示、损坏 APK 拦截、错误签名拦截、同版本安装拒绝、完整包恢复、只读 URI 和安装授权提示。
- 预览阶段的 v0.14 仅为独立测试 APK 内的未来版本夹具，用于下载状态、签名/完整性及界面验证；未发布、未安装。失败状态也使用明确的测试夹具，不冒充服务端真实故障。
- 系统安装授权尚未开启，因此真机验证到授权提示与安装 Intent/URI 合约，没有修改系统权限，也未执行夹具的最终安装。
- 覆盖安装本次本地包前后，4 个书架条目、5 条阅读位置、2 个书签及 ReaderActivity 设置均保持一致。安装后 base.apk 与本地构建逐字节一致。
- 真机状态截图和结果：`tests/update-evidence/update-checks/`；数据保留核对：`tests/update-evidence/preservation-result.json`。这些测试产物默认不进入 Git。

## v0.14 正式版发布准备

- 正式包版本 0.14 / versionCode 14，沿用原签名；交付文件 `releases/reading-dog-0.14.apk` 与 `releases/SHA256SUMS.txt`。
- SHA-256：`c165d05d85014a2bdda1d14ab66074f76406a886aeef167d15200f94bc1c5860`。
- 发布前真机 26 项检查通过；真实 Release API 请求正常。手机当前网络对 github.com 的 APK 下载出现超时/连接中断，独立 curl 也在重定向前超时。两项实时下载与下载后版本拒绝检查通过 `-e skipLiveDownload true` 明确跳过，未计入通过数；预览阶段的完整 28 项验证见上文。电脑端对同一真实 APK 的下载正常。
- 测试夹具版本改为随当前版本生成（0.14.1 / code 15），与即将发布的正式版明确分离；测试夹具未安装、未发布。
- 小米 6 已从 0.13 覆盖升级到 0.14，4 个书架条目、5 条阅读位置、2 个书签和阅读设置核对保留，手机安装文件与正式包一致。证据保存在 `tests/release014/`。
- 发布流程：提交源码到 main，再上传独立 `release-assets/v0.14` 分支的正式 APK 与校验文件，最后推送 v0.14 标签触发现有 Actions 的 build / publish。

首次使用更新功能的现有用户需先安装本版本。手机网络需能访问 GitHub 才能下载更新；连接失败时可在应用中重试。

复测命令：先运行 `build.ps1`，再运行 `tests/build-updates.ps1`，安装应用及独立检查 APK，然后在空闲的真机执行 `adb shell am instrument -w com.example.yuejie.updatechecks/com.example.yuejie.UpdateChecks`。测试不使用模拟器。

平台约定参考：[安装授权](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())、[ContentProvider 文件访问](https://developer.android.com/reference/android/content/ContentProvider#openFile(android.net.Uri,%20java.lang.String))。
