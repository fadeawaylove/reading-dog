# 番茄直连恢复与真机检查（2026-11-10）

本轮为未发布的 v0.29 原签名测试包。手机直接请求番茄搜索、详情、目录、章节字数元数据、加密正文和段评，以及上游封面／头像／评论图／正文图。后台保留同步、时间、更新、安装下载、字体和健康检查，无内容代理回退。

## 实现

- 以 v0.23 客户端协议为基础恢复直连，保留空响应冷却重试、身份与密钥绑定、段落版本校验；新版缓存、图片升级保护和评论预取不回退。
- `ChapterContent` 使用 Android 原有 HTML 解码结果，按 ImageSpan 提取 UTF-16 图片偏移、地址和尺寸。保留合法“一”、破折号及图片占位字符，不改写书签或进度偏移。
- `UpstreamImages` 共用图片下载限制，检查每次重定向的 HTTPS、域名和图片类型，限制 8 MiB，不发送后台凭据。正文保留原图与现有采样／内存上限。
- `NetworkWork` 负责正文优先与评论单通道调度，不依赖后台客户端。
- 真机发现翻页保存进度与评论缓存读取的锁反转：主线程持有 SQLite 事务后进入 synchronized acknowledgeRead，评论线程持有 LocalLibrary monitor 后等待 SQLite 连接。将 progress 写入改为先获取 monitor 再开始事务；不更改同步格式和数据库版本。

## 验证结果

- MI 6、Android 9：独立无后台凭据测试包，23 项直连检查通过（包含 9 项身份/空响应恢复回归和 612 个真实历史段落解析对照）。
- 真实上游：10 项检查通过，覆盖搜索、详情、目录、字数元数据、正文解密、段评统计/列表、封面、禁止网络后的正文缓存读取、进度不变。
- 60 项图片检查通过，包括 96 组纯文本排版对照、图片位图实际绘制、日夜截图、失败重试、旧缓存、书签/同步队列偏移、点击、缩放与翻页。MIUI 阻止 instrumentation 从后台启动 Activity 时，通过 ADB 显式启动同一个独立测试 Activity 后继续完成。
- ANR 回归先复现为 TimeoutException；修复后 300 次进度保存与 600 次章节缓存读取并发通过，最终偏移和 outbox 保持正确。
- 正式包从手机原 v0.22 保留数据升级：原 4 本书、2 个书签保留；同步合并后书架为 7 本，远端已有进度会按原同步规则更新。保留了原 APK 与完整应用数据备份。
- 修复死锁后再次覆盖安装，真实正文、段评展开及 12 次左右翻页已检查，无新增 ANR。测试前已有 ANR 文件保留作证据。
- 后端 29 项检查、9 项恢复检查、请求调度检查及 22 项协议向量通过；本地 HTTP 和公网检查确认 9 个内容/媒体路由返回 404，health/time/sync/update/font 仍正常。
- 全部现有 Android 测试源编译通过；APK v2/v3 签名验证通过。纯编译检查不代表所有历史测试均已运行。

复跑：`backend/test.ps1 -NativeVectors`、`python backend/test-routes.py`、`android-replica/tests/build-images.ps1`，然后在独立包运行 `DirectChecks`、`DirectChecks -e phase live`、`ImageChecks` 和 `LibraryLockChecks`。使用标准 `adb shell am instrument -w [-e phase live] com.example.yuejie.imagefixture/com.example.yuejie.<检查类>`。真实上游用例依赖网络与指定作品可用性。

## 交付与部署

- 测试包：`artifacts/direct-20261110/reading-dog-0.29-direct-test.apk`，已安装至当前 MI 6。
- SHA-256：`d2a1fade654310a5411435487c55a170c09aaa3a5312cb98d44005feb7de7d3c`。
- 图片、升级、段评截图及私有备份位于同一忽略目录；备份和测试包含私人数据或配置，不应提交 Git。
- Docker 已部署职责收缩；服务目录 `/opt/reading-dog-backend`。回滚镜像 `reading-dog-backend:before-direct-20261110`，原 JAR、Dockerfile、Compose 与同步数据库快照在 `rollback-direct-20261110/`。数据库未清空或迁移；回滚程序使用旧镜像和旧 JAR，正常情况下不要回退数据库，以免丢失后续同步。
- 没有发布新 APK、改变版本号或替换线上更新文件。其他仍使用 v0.24–v0.29 代理版的设备需要手动安装本次直连测试包，旧内容路由已停用。

## 未完成验收

异常“一”没有取得可复现章节：原手机缓存的 61 章没有 U+FFFC，升级后当前阅读章节也未发现多余字符。未执行任何全局字符过滤，不能声称该现象已修复；需要具体书名、章节、位置或截图继续定位。真实含图片的上游章节尚未验收，图片渲染与缩放结果来自明确标记的构造图片。长时间阅读稳定性、全套离线 UI／设置组合尚未完成。

现有同步协议同步书架和阅读进度；书签保持本地且升级保留，本轮没有新增跨设备书签同步。

## 破折号显示差异跟进（同日）

用户提供 iQOO 的第48章截图后，在 MI 6 缓存核对到句子“立刻感受到了不对劲——和之前的寂静不同”，横线原文为两个 U+2014，非 U+FFFC 或插入的汉字。进一步实际 Canvas 对照复现了排版问题：两端对齐在破折号内部增加间距，且系统衬线字体的逐字 drawTextRun 与完整原生 run 绘制结果不同。v0.27 从固定内置字体切到默认系统字体，可能使旧绘制缺陷在不同设备上显现；不能只归因于正文图片改动。

ReaderTextLayout 现在把连续 U+2014 作为一个原始字符区间进行测量、换行、对齐和绘制，避免内部拉伸及拆分字体成形结果。正文、书签和进度字符偏移不变。BookPageIndex profile 升为 v14，旧排版页数按需重算。

独立 DashChecks 修复前 12 项中有 5 项失败；修复后扩展为 16 项全部通过，覆盖 MI 6 系统字体、系统衬线、内置黑体、内置宋体的原生像素对照、原句六档宽度（含极窄宽度）、不拆行，以及普通中文/单破折号/英文/emoji 在三档字号和三档宽度下与修改前的行边界和字位置一致。测试包不含私有后台凭据，字体仅为测试资源。

构建 `tests/build-dashes.ps1`，安装独立包后运行 `adb shell am instrument -w com.example.yuejie.dashfixture/com.example.yuejie.DashChecks`。前后图片位于 `artifacts/direct-20261110/dash-evidence/`。

最新测试包 `artifacts/direct-20261110/reading-dog-0.29-dash-test.apk` 已保留数据覆盖安装至 MI 6；包含本轮直连和死锁修复。SHA-256：`8ce07685f76ce9ad52695d94e6125f6b6e57a66faf88c4580329fda3eb260d9e`。未发布线上版本。iQOO 的具体系统字体尚未实机验证，需安装本包核对；各机型字体笔画外观仍可能不同，本次修复排除程序拆分及内部拉伸，不强制两台手机像素完全一致。

## 默认测试的夹具独立性修复

默认 `tests/build-images.ps1` 不再自动复制本机历史正文；重复构建会移除上次打包的 `text-parity.json`。默认 `DirectChecks` 运行合成正文、请求路由与协议恢复检查，不读取历史语料。上述 23 项是包含历史样本的先前结果，当前默认为 22 项。

历史正文对照是单独的显式阶段：

```powershell
./android-replica/tests/build-images.ps1 -ParityFixture C:/path/to/text-parity.json
# 安装生成的独立 checks.apk 后运行：
adb shell am instrument -w -e phase parity com.example.yuejie.imagefixture/com.example.yuejie.DirectChecks
```

传入不存在的路径会在构建开始时失败；未打包夹具却主动运行 parity 阶段也会报告失败，不把未执行冒充通过。夹具使用非空 JSON 数组，每项含 `html`，样本数量不硬编码。私有历史正文不提交仓库；默认构建/测试无需它。已有本机协议向量的开发者可以运行 `backend/test.ps1 -NativeVectors` 生成历史段落语料，再明确传入产物路径。

验证：默认 APK 不含历史夹具及后台凭据；显式历史模式的 612 段通过；随后重新默认构建确认移除旧夹具，MI 6 上默认 22 项检查连续两次通过。恢复用例每次使用独立合成书籍 ID，避免旧缓存跳过被测请求；显式不存在的夹具路径验证为提前报错。

## v0.30 正式发布（2026-11-10）

已按用户要求发布到私有更新服务，versionName=0.30、versionCode=30，包含上述直连、ANR、破折号修复及可复现测试调整。此发布状态取代前文“尚未发布”的历史状态。原签名与包名校验通过，服务端版本文件及 latest 元数据已更新；公网 HTTPS 回下载 APK 与本地 SHA-256 一致。

本地交付 `artifacts/release-0.30/reading-dog-0.30.apk`；SHA-256：`657f1d39beddc7b3c9552fc32c9faaae8e4842178940faaa995c261420fd92ca`。可从应用「关于阅读狗 → 检查更新」安装。未创建 GitHub Release；本次发布未自动升级测试手机。iQOO 系统字体实机确认的限制仍保留。
