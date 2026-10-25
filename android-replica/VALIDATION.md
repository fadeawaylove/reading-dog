# 真机验证记录

**最新 B 版单页主页验收见 [SINGLE-HOME.md](SINGLE-HOME.md)**。

**正文空响应与导航小字的最新修复见 [EMPTY-RESPONSE-FIX.md](EMPTY-RESPONSE-FIX.md)**。

**阅读页最新改造结果见 [READER-REDESIGN.md](READER-REDESIGN.md)**。下文为首次四接口集成验收记录，阅读截图和旧交互不代表当前版本。

设备：小米 6，Android 9 / API 28，ADB serial `6da02a7`。2026-10-24 在实际手机执行，未使用模拟器。

## 结果

- 按用户指示使用 Magisk root `pm install -r` 安装，正式包和独立测试包均返回 Success；包名及原 debug.keystore 保持不变。
- Android 上 22 组协议对照全部通过：7 个 Argus、6 个 Ladon/Helios、SM3 标准向量、注册请求、会话密钥、6 个正文解密向量。
- 四类真实接口通过：搜索首/次页得到 20/10 本并包含不同书籍；《我在精神病院学斩神》详情正常；完整目录 2033 章；前两章正文 3572/2386 字符，SHA-256 与保留的独立结果一致。
- 完整流程 19 项检查通过：快速切换查询、搜索分页、详情、加入本地书架、重复加入幂等、原生分页、字号重排保留字符偏移、夜间、书签、退出续读、跨章往返、书架恢复。
- 独立进程重启检查 6 项通过，恢复第 2 章《月亮上的天使》及保存的偏移，书架仍有测试书。
- 在 Android StrictMode 禁止当前测试线程联网的条件下读取已缓存章节成功，客户端没有注册密钥/发出正文请求。此项验证缓存路径，不是切断整部手机的 Wi-Fi。
- 旧 books_v2 导入、重复迁移、书架重启、书签增删和 50 MiB LRU 逻辑容量检查通过；迁移/容量夹具使用隔离 checks-* 数据库，不覆盖正常书架。
- 实际多次覆盖安装后保留本次加入的书及阅读状态；手机起初没有旧 0.1 包，未声称验证了真实旧用户数据样本。

原始结果：

- [完整流程 JSON](tests/device-evidence/verified/device-results.json)
- [进程重启和缓存 JSON](tests/device-evidence/verified/cold-results.json)
- [最终 APK 信息](tests/validation-summary.json)

## 视觉核对与修正

已查看设计 HTML、设计截图，以及手机上的发现、书架、阅读、夜间和目录截图。落实纸色、墨绿、中文衬线标题与正文、0.7 书封比例、三列书架、继续阅读卡、底部导航与阅读工具栏。

真机检查后修正：固定正文行高与段间距、页码初始布局、截图等待页面切换完成、夜间目录按钮配色、封面任务去重/优先级/磁盘缓存，以及继续阅读卡的绿书签装饰。

- [发现](tests/device-evidence/verified/discover.png)
- [书架](tests/device-evidence/verified/shelf-final.png)
- [阅读](tests/device-evidence/verified/reader-final.png)
- [夜间阅读](tests/device-evidence/verified/reader-night.png)
- [目录](tests/device-evidence/verified/directory.png)

这些是自动化操作真实 Activity 的检查及截图核对，不代替用户主观手感验收。某些封面响应无法被系统解码时保留书名占位；已确认测试书真实封面正常显示。

## 搜索空响应恢复

复测观察到：一份此前接受的匿名配置在后续请求中返回 HTTP 200 空正文，普通重试不能恢复；重新初始化后同样搜索立即成功。尚不据此断言服务端的具体失效原因或期限。

App 仅在搜索首页收到空正文时重新初始化并重试一次，两次初始化之间至少间隔 60 秒；不会无限循环。最终真机日志包含恢复动作及随后成功的搜索请求。分页及其他失败保留错误与重试入口，HTTP 200 空正文始终不是成功结果。

## 复现

先运行仓库根目录的 `android-replica/build.ps1` 和 `android-replica/tests/build.ps1`。允许 root 后安装：

```powershell
$deviceAdb = 'C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $deviceAdb push android-replica/build/yuejie.apk /data/local/tmp/reading-dog.apk
& $deviceAdb shell su -c 'pm install -r /data/local/tmp/reading-dog.apk'
& $deviceAdb push android-replica/tests/build/checks.apk /data/local/tmp/reading-dog-checks.apk
& $deviceAdb shell su -c 'pm install -r /data/local/tmp/reading-dog-checks.apk'
& $deviceAdb shell am instrument -w com.example.yuejie.tests/com.example.yuejie.DeviceChecks
& $deviceAdb shell am instrument -w -e phase resume com.example.yuejie.tests/com.example.yuejie.DeviceChecks
```

测试证据保存在目标 App 私有目录 `files/device-evidence`；通过 root 复制到专用临时目录再拉取。测试包与正式包分离，正式 APK 不包含测试入口、Python、原下载器或 WebView 阅读器。


## 0.3 图标、预缓存、作者与进度

见 [READING-IMPROVEMENTS.md](READING-IMPROVEMENTS.md)，小米 6 本轮 14 项通过；包含真实缺失章节预缓存和禁止联网读取。


## 0.4 连载更新章末翻页

[修复与真机回归](CATALOG-UPDATE-FIX.md)：旧缓存 611 / 实时目录 612，末章翻页自动打开新增真实正文，失败重试与无更新提示通过；保留用户原文偏移。


## 0.5 番茄式翻页设置

[实现与真机证据](PAGE-TURN-SETTINGS.md)：仿真、覆盖、平移、无动画四种方式，最终专项 11 项、既有阅读回归 19 项及强制停止进程后的偏好/原文位置恢复通过；四段真机录屏和逐帧图可查看。


## 0.6 段评

独立接口验证、23 项真机专项和 5 项真实断网检查见 [PARAGRAPH-COMMENTS.md](PARAGRAPH-COMMENTS.md)。最终安装 APK 与 `releases/reading-dog-0.6.apk` SHA-256 一致；3 本书、全部阅读位置、书架最近阅读时间及书签保留。


## 0.7 阅读正文对齐

对照手机上的番茄阅读页修复正文偏左；修复前边距 20/48dp，修复后 20/20dp。45 组几何回归及 23 项段评真机回归通过，见 [TEXT-ALIGNMENT-FIX.md](TEXT-ALIGNMENT-FIX.md)。


## 0.8 实际字形中心

在 0.7 排版框检查之外增加位图像素断言：修复前该断言失败，修复后 30 组字体/字号/宽度组合通过，45 组分页/气泡检查通过，并在用户当前第610章进行实际左右滑动和位置保留检查。详见 [TEXT-ALIGNMENT-FIX.md](TEXT-ALIGNMENT-FIX.md)。


## 0.9 首页后台刷新与下拉刷新

[实现与真机验证](SHELF-REFRESH.md)：9 项专项通过，实际目录更新到拼好饭 613 章、持械入宋 484 章；系统级下拉确认三本书检查时间更新，schema 1→2，阅读位置、书架顺序、书签保留。


## 0.10 四种翻页绘制

三原生页容器轮换、Scroller结算、真实折页几何与无动画刷新控制。证据及验收边界见 [TOMATO-PAGE-TURN-PARITY.md](TOMATO-PAGE-TURN-PARITY.md)。墨水屏物理刷新尚待设备验收。
