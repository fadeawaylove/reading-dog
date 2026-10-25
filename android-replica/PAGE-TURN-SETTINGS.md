# 番茄式翻页设置与四种动画（0.5）

## 最终界面

按用户纠正后的要求，取消最初的图示卡片方案，采用手机上番茄阅读设置的布局与控件：左侧标签、右侧整条圆角分段选项、浅色胶囊选中态和加粗文字。亮度使用宽轨道与圆形滑块，字号采用胶囊加减按钮，字体通过选择框切换，背景用圆形色块；底部保留目录、书签、夜间、设置入口。正文不因面板出现或模式切换而重新分页。

通过 REA 截取已安装番茄的实际设置，并录制了仿真翻页参考。其选项是仿真、覆盖、平移、上下、无动画；阅读狗按本轮约定只实现四种横向方式。没有加入上下滚动、自动阅读或听书。

- [番茄实际设置](tests/page-turn/reference-settings/screen.png)
- [番茄仿真参考录屏](tests/page-turn/reference-curl.mp4) / [参考逐帧图](tests/page-turn/reference-curl-frames.png)
- [阅读狗最终日间设置](tests/page-turn/verified/screens/cover-settings-day.png)
- [阅读狗最终夜间设置](tests/page-turn/verified/screens/simulation-settings-night.png)

## 实现与兼容

- `PageTurnMode` 提供稳定偏好值 `simulation / cover / slide / none`。旧用户、缺失值和未知值默认覆盖；选择立即持久化。改变模式不触发正文重排，不修改字体、章节或原文偏移。
- `PageTurnRenderer` 从 Surface 分离绘制策略：仿真以贝塞尔曲线裁剪折线、反射背页与纸色/阴影组合；覆盖为页面覆盖相邻页；平移为两页同时移动；无动画拖动中保持原页，确认后立即切换。
- 共用左右点击、拖动、回弹和提交逻辑。完成阈值为屏宽 23% 或沿翻页方向 650dp/s；点击 220ms，释放按剩余距离计算、最低 100ms。无动画模式不创建补间动画。动画期间新的触摸序列忽略到抬手，防止中途误提交。
- 仿真最多准备当前/前/后三张页图，在页面或相邻章节准备好后生成；拖动只取用已有页图，不请求网络、不分页、不创建整页位图。尺寸、字体、主题、段评数据变化后更新缓存，暂停/离开释放位图并取消未提交动画。
- 阅读位置仍是章节 ID 和原文偏移；章节加载、失败重试、预缓存和末章强制刷新目录继续沿用。没有修改数据库版本或包名。

## 真机验证

设备为小米 6，Android 9 / API 28，1080×1920。使用现有签名 root 覆盖安装。

| 验证 | 结果 |
| --- | --- |
| [最终专项](tests/page-turn/verified/turn-final-results.json) | 11 项通过：22 组协议向量、缺省兼容、四种模式、字号重排与夜间、长标题/Unicode/三档字号、正文失败重试、旧目录末尾联网发现下一章、Activity 重建 |
| [既有阅读回归](tests/page-turn/verified/reader-regression.json) | 19 项通过：真实设置控件、像素露出检查、取消/回弹、OS 手势、跨章、9 组排版参数、目录/书签及网络错误恢复 |
| [冷启动](tests/page-turn/verified/cold-final-results.json) | 显式强制停止 App 后，新进程恢复仿真偏好和相同章节/原文偏移 |
| [数据保留](tests/page-turn/verified/data-preservation.json) | 原有 3 本书、4 条阅读位置及原有书签与安装前一致；测试产生的书签和阅读偏好已清理/恢复 |

每种模式单独验证了双向点击、OS 注入滑动、拖动完成/短拖回弹/ACTION_CANCEL、快速翻页、暂停取消、中间菜单、跨章往返、日夜面板及模式切换不重排。仿真检查了实际背页/曲线路径与拖动期间位图分配数不增长；无动画检查拖动前后正文渲染像素一致。

旧阅读测试最初的右侧点击落在已接入的段评气泡上，触发了段评面板；基础翻页回归现显式关闭段评以隔离该测试。字体和背景的旧文本点击选择器也已迁移至新选择框/色块。没有改变实际段评点击行为。

已查看四种真机录屏、日夜截图和逐帧图：仿真可见折页背面，覆盖可见目标正文，平移两页衔接，无动画保持原页直至确认。最终专项中的绘制方法平均 CPU 时间约 0.9–6.3ms；该值包含各模式测试期间的绘制采样，不代表完整 GPU 帧时间或恒定 60fps。

| 模式 | 真机录屏 | 逐帧图 |
| --- | --- | --- |
| 仿真 | [simulation.mp4](tests/page-turn/verified/simulation.mp4) | [逐帧](tests/page-turn/verified/simulation-frames.png) |
| 覆盖 | [cover.mp4](tests/page-turn/verified/cover.mp4) | [逐帧](tests/page-turn/verified/cover-frames.png) |
| 平移 | [slide.mp4](tests/page-turn/verified/slide.mp4) | [逐帧](tests/page-turn/verified/slide-frames.png) |
| 无动画 | [none.mp4](tests/page-turn/verified/none.mp4) | [逐帧](tests/page-turn/verified/none-frames.png) |

## 交付与复测

APK：`build/yuejie.apk`，版本 0.5 / versionCode 5。SHA-256：`0e71d9435e9c6e8934e79c0db0274a829c1ec4ef1ea30d349741f4a8df02bcd6`，与手机安装的 base.apk 一致。签名证书沿用 `3d036c2956aa9369777eb0966aa61ea6c84870a2c87860cd8e8c020ff8990bb8`。测试 APK 已卸载，正式 App 留在单页主页，用户原有阅读设置已恢复。

构建仍使用现有 `build.ps1`。独立测试 APK 增加 `TurnChecks` instrumentation，`mode` 可传 `simulation / cover / slide / none / regression`，省略时运行完整专项；测试代码不进入正式 APK。四段录屏在最终翻页实现上采集，重新编译完整工作区后又运行了完整专项与既有阅读回归。

```powershell
.\android-replica\build.ps1
.\android-replica\tests\build.ps1
# 安装正式和测试 APK 后，以 root 运行：
adb shell su -c 'am instrument -w com.example.yuejie.tests/com.example.yuejie.TurnChecks'
```
