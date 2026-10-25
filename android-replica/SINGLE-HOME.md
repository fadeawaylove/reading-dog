# B 版单页主页实施与验收

按用户选定的“阅读清单”方案实现。仅 MainActivity 一个主页，自上而下为搜索、最近阅读、书架。去除旧发现页、分类、推荐与底部导航；启动主页不再请求推荐数据。

## 行为

- 暖纸色背景、下划线搜索、衬线最近阅读书名、圆形续读箭头、细阅读进度线。
- 书架为紧凑单列，使用小书封、完整书名（最长两行）、真实章节标题和轻量箭头；长按保留详情/移出。
- 最近阅读与书架从既有 SQLite 读取；不迁移或清空书架/阅读偏好。旧 selected_tab 偏好不再控制启动页面。
- 键盘搜索或点击左侧放大镜提交；结果在主页内显示。清空按钮、清空输入或返回键恢复普通主页。
- 请求继续使用已验证的独立 Java API、分页及空响应恢复。搜索词变化/清空后使旧回调失效，避免晚到结果占据主页。
- 从详情/阅读返回后更新本地记录，保留相应搜索状态和滚动位置。

## 实测

设备：小米 6，Android 9 / API 28。以现有签名通过 root `pm install -r` 覆盖安装成功，并已打开新主页。

- [主页回归 JSON](tests/single-home/verified/home-results.json)：10 项通过，包含 Android 22 组协议向量、存储回归、空主页、最近阅读续读/返回、真实搜索 20→40 本、详情返回、清空恢复、迟到结果拦截。
- 最终小改（书架章节状态改用真实标题）后再次验证三个区域、无底部导航，4 项通过。
- 安装前后数据库文件 SHA-256 相同；功能检查后再次比较原有书籍 ID 与所有阅读章节/偏移，原有 2 本书、3 条阅读位置完全保留。[数据保留摘要](tests/single-home/data-preservation.json)
- 已查看最终真机主页、空主页和搜索截图，对照 B 版的颜色、结构、字号层级与留白。

[最终真机主页](tests/single-home/final/screen.png) · [空主页](tests/single-home/verified/empty-home.png) · [搜索结果](tests/single-home/verified/search-results.png)

测试空状态使用隔离数据库，未清空用户书架。测试没有为了展示效果填充设计图中的示例书。

## 交付

APK：`build/yuejie.apk`。包名、签名和数据库保持原样。原生 Java 技术栈，阅读器及最近一次正文空响应修复继续保留。

复测主页流程：安装独立测试 APK 后执行：

```powershell
$readerAdb = 'C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $readerAdb shell su -c 'am instrument -w -e phase home com.example.yuejie.tests/com.example.yuejie.DeviceChecks'
```
