# 阅读狗 · 极简单页主页

Android 原生 Java，包名 `com.example.yuejie`，版本 0.14（由 `version.json` 统一配置）。四类接口直接由手机请求，不需要 Python、电脑服务或原下载器。

## 功能

- 检查更新：搜索栏右侧“关于阅读狗”→“检查更新”，显示正式版更新说明、下载进度及失败重试；校验安装包后交给系统安装。实现与真机验证见 [APP-UPDATE.md](APP-UPDATE.md)。
- 主页：B 版暖纸色阅读清单，自上而下只有搜索、最近阅读和书架；没有发现/书架分页、推荐卡、分类或底部导航。
- 搜索：在当前主页显示结果和分页；清空输入返回本地书架，过期请求不会覆盖主页。
- 详情：作品信息、完整目录、加入/移出本地书架、首次/继续阅读。
- 最近阅读：突出最后阅读的书名、作者、章节标题与目录位置/总章节，点击继续原位置。书架使用紧凑列表，显示缩略封面、书名、作者、目录位置/总章节及真实章节标题；长按查看详情或移出。
- 阅读：原生段落分页、沉浸正文、仿真、覆盖、平移、无动画四种横向翻页，中间点击呼出浮层菜单；跨章衔接、目录/书签面板、16–30sp 字号、三档行距、黑体/宋体、四种背景及应用内亮度。章节大标题仅在章首页出现。
- 翻页设置按番茄式胶囊分段控件实现，模式立即生效并持久化，不重排正文；本轮证据：[PAGE-TURN-SETTINGS.md](PAGE-TURN-SETTINGS.md)。
- 阅读细节和原始真机证据：[READER-REDESIGN.md](READER-REDESIGN.md)。
- 阅读位置以章节 ID 和原文字符偏移保存，改变字号后定位到包含该偏移的页面。
- 缓存目录和已读章节，预取后续 5 章（紧邻章预排版，额外 4 章串行写入磁盘；离开阅读停止排队预取）；章节有效负载按最近访问淘汰，合计不超过 50 MiB（SQLite 文件可能保留空闲页）。

主页采用用户选定的 B 版：暖纸色 #f5f1e8、文字 #383a33、细线搜索框、宋体最近阅读标题、紧凑书架列表；书封宽高比 0.7。阅读页沿用番茄式沉浸体验。最新主页验收见 [SINGLE-HOME.md](SINGLE-HOME.md)。

本轮图标、预缓存与作者/进度验收见 [READING-IMPROVEMENTS.md](READING-IMPROVEMENTS.md)。

连载末章自动检查更新修复见 [CATALOG-UPDATE-FIX.md](CATALOG-UPDATE-FIX.md)。

进入阅读时先显示缓存正文，再静默刷新一次目录；发现追加的新章后补入后续五章缓存，并提前排版紧邻下一章。进入最新目录的最后五章范围后，每进入一章再静默检查一次；同章翻页、重排和停留不会重复请求，没有定时轮询。退出阅读后停止本轮检查，返回阅读时重新检查。失败静默保留正文，末章继续保留主动检查和重试入口。验证记录见 [入口刷新](ENTRY-DIRECTORY-UPDATE.md)和[最后五章检查](PROGRESS-DIRECTORY-UPDATE.md)。

首页后台刷新、下拉刷新和新章标记见 [SHELF-REFRESH.md](SHELF-REFRESH.md)。固定交付 APK 位于 `artifacts/reading-dog-0.9-shelf-refresh.apk`。

## 构建

在仓库根目录运行 PowerShell 7：

```powershell
.\android-replica\build.ps1
```

使用现有 `C:\Android\Sdk` 和 Android Studio JBR，无需 Gradle、额外依赖或网络下载。产物 `android-replica/build/yuejie.apk`，保留原 signing/debug.keystore。

## 接口与存储

- `NovelApi`：search(keyword, offset, searchId, passback)、detail(bookId)、directory(bookId, force)、chapter(bookId, chapterId)。同步方法仅由工作线程调用；回调返回主线程。
- 搜索沿用独立验证的 68132 配置；正文沿用 74132 配置，匿名身份分别持久化。签名包括 Argus、Ladon、Helios、Khronos，AES 使用 Java Cipher；SM3/Simon/封装和密钥协议为独立 Java 移植。
- `ProtocolCrypto` 不依赖 Android，可在 JVM 对照原程序捕获和既有 Python 结果。
- `LocalLibrary` 保存 books/progress/directories/chapters/bookmarks。原 reading_dog/books_v2 事务导入成功后才设置 sqlite_migrated，原 JSON 不删除。ID 全程为字符串。
- 详情解析网页结构化初始状态；目录校验与 allItemIds 完全一致。正文只解析文本，不执行服务端 HTML/JS。
- 重试会显示 HTTP/业务/解密错误；HTTP 200 空响应不当作成功。搜索首页和正文各自支持最多一次匿名配置重新初始化与重试，并设 60 秒间隔；正文密钥与对应匿名身份绑定。详见 [EMPTY-RESPONSE-FIX.md](EMPTY-RESPONSE-FIX.md)。
- 封面使用独立工作线程、请求去重与 16 MiB 磁盘缓存，优先当前详情/书架请求，避免阻塞接口工作线程。

## 验证

详见 [VALIDATION.md](VALIDATION.md)。测试 APK 独立于发布 APK，不向正式 App 加入测试入口。已按用户指示以 root 安装并在小米 6 完成四类接口、完整流程、进程重启续读、缓存及截图核对。详细边界和原始证据见验证记录。


## 阅读页段评（0.6）

段尾气泡打开只读段评面板，支持头像、文字、点赞数、配图预览、游标分页、日夜主题与缓存。菜单“更多”可隐藏或重试；保留侧会话的四种翻页方式和目录更新修复。详见 [PARAGRAPH-COMMENTS.md](PARAGRAPH-COMMENTS.md)。


0.7 修复段评导致的正文偏左：恢复左右各 20dp，气泡跟随段尾；参考与真机回归见 [TEXT-ALIGNMENT-FIX.md](TEXT-ALIGNMENT-FIX.md)。


0.8 补充修正整列汉字的实际视觉中心：按当前字体测得的汉字宽度计算完整文字列，将不足一字的余量均分到两侧。已增加实际字形像素测试，见 [TEXT-ALIGNMENT-FIX.md](TEXT-ALIGNMENT-FIX.md)。


四种翻页绘制改造 0.10 见 [TOMATO-PAGE-TURN-PARITY.md](TOMATO-PAGE-TURN-PARITY.md)，固定交付 APK：`releases/reading-dog-0.10.apk`。


## 全书页码与总进度（2026-10-25）

阅读页左下角使用全书页码和全书百分比。已通过 Florida 在番茄 7.3.9.32 真机确认全书页码开关开启、`mo7.s` 逐章模型预测及真实排版校正、`i0` 字数加权进度分支。`TomatoPageModel` 保留该版本的模型参数，使用本应用当前字号、行距、段距及正文区域计算；`BookPageIndex` 累计各章页数，当前及相邻章排版完成后校正，并按排版配置缓存真实页数。两种应用排版不同，总页数不保证相同。

每章字数来自独立验证的原生 `/reading/bookapi/directory/all_items/v/` 接口，完整性校验后缓存；不以全书平均字数替代，不下载全书正文。元数据缺失时显示加载状态和重试，不伪造页数。目录新增章节必须补齐元数据；字体、字号、间距、段评布局或尺寸改变后重算，保留章节 ID 和字符偏移。

真机证据：`../research/page-count/runtime.jsonl`、`runtime-font.jsonl`、`metadata-probe.json`。同一 209 章目录：字号 24 模型合计 5007，三章实际校正后 5010；字号 25 合计 5263，校正后 5268，与番茄真机一致。本应用 14 项专用真机检查及独立进程冷重启续读通过，记录在 `tests/global-pages/verified/`；专用测试源 `tests/PageChecks.java`，构建 `tests/build-pages.ps1`。现有书架、阅读位置与阅读偏好在测试结束后恢复。

补充验收：现有 `ParityChecks` 翻页渲染回归通过（含无动画拖动零额外重绘、三页复用、四模式提交/回退、手势期间延迟视觉更新及仿真几何）；汇总 `tests/global-pages/validation-summary.json`。安装包 SHA-256 `bc22de70d81249debcb7a4faeef583f7b8dc47b0ee4d4524cca6007ebaeaf163`，与手机已安装 base.apk 一致。专用测试 APK 已卸载，正式 App 保留。
