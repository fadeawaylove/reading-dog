# 阅读页重做与真机验收

日期：2026-10-24。设备：小米 6，Android 9/API 28，1080×1920。保持原生 Java、包名、签名、书架数据库与原文阅读偏移。

## 实际参考与最终行为

通过 REA 截取了已安装番茄 7.3.9.32 的实际阅读页和中间点击后的菜单，并实际执行从书籍封面左滑进入阅读。参考的是正常正文、覆盖菜单和章节进度布局，不复制登录、广告、金币或听书入口。

- 正常状态隐藏系统栏和菜单，使用完整屏幕正文区；顶部小号章节提示，底部当前章页码、估算全书进度、时间和电量。
- 默认黑体、20sp、1.65 倍行高、20dp 左右边距、两字缩进、10dp 段距；已有字号和夜间偏好继续保留。
- 大标题只出现在章首页；续页从顶部直接接正文，原文不插入视觉缩进或空行。
- 左/右各 30% 点击翻页，中间 40% 呼出菜单。菜单和设置在正文之上覆盖，不改变页面几何，不重排正文。返回键依次关闭面板、菜单、阅读页。
- 左右覆盖动画随手指移动，达到距离/速度阈值完成，否则回弹；取消不提交进度。动画期间额外触控被忽略，不会同时提交两次翻页。
- 设置包含字号 16–30sp、三档行距、黑体/宋体、纸白/米黄/护眼/夜间、应用内亮度和自动恢复系统亮度。
- 目录与书签直接在底部面板切换，当前章定位、刷新、点击跳转；顶部更多可添加/移除本页书签，书签列表长按移除。
- 目录/正文缓存继续沿用。相邻章节后台加载并排版；拖动和绘制中不做网络请求或重新分页。失败保留当前页并提供重试。

## 模块与数据兼容

- `ReaderPaginator`：按段落排版，记录原文连续起止偏移和页面绘制片段；首页独立标题区域，分页断言原文覆盖完整。
- `ReaderSurface`：仅组合当前页和相邻页布局，负责触摸、回弹/覆盖动画、页脚绘制；动画完成后才提交位置。
- `ReaderSettings`：沿用 ReaderActivity 偏好并增加行距、字体、背景、亮度设置；不修改书架和进度表。
- `ReaderActivity`：菜单、面板、章节获取、位置保存和相邻预加载；字号等变化时异步排版，通过代次忽略旧结果。
- 正文偏移仍以原始字符串为准，视觉布局不修改缓存原文。翻页提交新页起点；设置重排定位到包含既有偏移的页面。

## 真机结果

[阅读器测试原始 JSON](tests/reader-redesign/verified/device-results.json)：19 项全部通过，其中每项包含多条断言。

- Android 22 组签名/密钥/正文固定向量，以及原有隔离书架迁移、缓存淘汰和书签存储检查通过。
- 同一章、20sp 下：旧实现 34 页，新实现 20 页；这反映正文空间与段距调整，不意味着采用固定页数。
- 中间点击不翻页；菜单不改变正文宽高；返回顺序正确。
- 实际字号按钮、行距/字体/背景选择、应用内亮度滑条与恢复自动均通过。
- 半拖动时，下一页露出区域与单独渲染的目标页逐像素一致，并确认露出正文不是空白；短拖回弹和 ACTION_CANCEL 不保存进度。
- 通过 root 启动 instrumentation 后，系统触屏事件注入完成实际拖动翻页；普通 shell 的注入受 MIUI INJECT_EVENTS 限制，没有修改系统权限设置。
- 9 组字号/行距组合、5 组短文/长段/空行/长标题/扩展字符 fixture 均验证原文连续覆盖、不拆 surrogate、不截底行。
- 已预取章节双向跨章成功；受控网络失败保留当前帧，点击重试切换成功。该失败由测试源抛出 IOException，不是断开整部手机网络。
- 通过原生目录列表点击跳到第 3 章，通过书签列表点击恢复章节及偏移。
- 当前测试的动画 onDraw 平均 CPU 用时约 4.4ms；这是绘制方法计时，不是完整 GPU 帧率或保证 60fps。

[独立进程重启 JSON](tests/reader-redesign/verified/cold-results.json)：6 项通过。恢复第 2 章《月亮上的天使》、非零原文偏移 149；默认沉浸状态、偏好保留。StrictMode 禁止测试线程联网时，缓存正文读取通过。

## 视觉与动画证据

实施前已展示三状态方案，并用真实手机截图核对最终版。

- [三状态方案](tests/reader-redesign/reader-proposal.png)
- [番茄阅读参考](tests/reader-redesign/reference-reader/screen.png) / [番茄菜单参考](tests/reader-redesign/reference-menu/screen.png)
- [新版阅读首页](tests/reader-redesign/verified/reader.png) / [续页](tests/reader-redesign/verified/continuation.png)
- [菜单](tests/reader-redesign/verified/menu.png) / [设置](tests/reader-redesign/verified/settings.png)
- [半拖动](tests/reader-redesign/verified/half-drag.png) / [夜间](tests/reader-redesign/verified/night.png)
- [目录](tests/reader-redesign/verified/directory.png) / [书签](tests/reader-redesign/verified/bookmarks.png)
- [录屏](tests/reader-redesign/cover-final.mp4) / [覆盖动画逐帧图](tests/reader-redesign/cover-final-frames.png)

逐帧复核曾发现背景绘制清空下一页露出区域，已通过页面范围裁剪修正，并新增像素回归检查。当前录屏逐帧可见下一页正文随拖动露出，无空白替代；已复核菜单和设置显示、正文留白与续页标题行为。

## 构建与复测

继续使用 build.ps1 和现有 debug.keystore，产物 `build/yuejie.apk`。正式包与独立测试包分离。

```powershell
.\android-replica\build.ps1
.\android-replica\tests\build.ps1
$readerAdb = 'C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $readerAdb push android-replica/build/yuejie.apk /data/local/tmp/reading-dog.apk
& $readerAdb shell su -c 'pm install -r /data/local/tmp/reading-dog.apk'
& $readerAdb push android-replica/tests/build/checks.apk /data/local/tmp/reading-dog-checks.apk
& $readerAdb shell su -c 'pm install -r /data/local/tmp/reading-dog-checks.apk'
& $readerAdb shell su -c 'am instrument -w -e phase ui com.example.yuejie.tests/com.example.yuejie.DeviceChecks'
& $readerAdb shell su -c 'am instrument -w -e phase resume com.example.yuejie.tests/com.example.yuejie.DeviceChecks'
```

这是已安装番茄版本的参考对齐与真机自动化/逐帧核对，不宣称与所有番茄版本逐像素相同，也不替代用户手动体验反馈。仅测试已验证可读章节；未测试的其他机型和付费/受限章节行为不扩展为通过。
