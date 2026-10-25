# 无动画翻页与番茄的静态对照

日期：2026-10-25。范围：检查当前阅读狗源码，对照本地番茄小说 7.3.9.32 APK；本轮没有修改 App、安装 APK，也没有墨水屏实测。源码差异不能当成用户所述突兀感的已确认根因。

## 阅读狗

- `ReaderSurface.animateTo` 的 NONE 分支不创建 ValueAnimator，直接提交目标 Frame。`PageTurnRenderer.None.draw` 仍绘制当前页。
- `drawFrame` 在同一次 onDraw 内绘制背景、标题、正文、段评和页脚；没有主动提交一张只有背景的空白帧。Canvas.drawColor 后再画正文不等于屏幕先白一下再显示文字。
- ACTION_MOVE 在 NONE 下仍每次 invalidate，尽管输出仍是原页。读者拖动时会产生无视觉变化的重绘请求。
- 成功提交链 animateTo → Host.commit → ReaderActivity.commit → setFrame → cancelAnimation；cancelAnimation、setFrame 和 animateTo 各自 invalidate，共三个直接重绘请求。Android 可合并这些调用，不能据此声称实际有三帧或三次墨水屏刷新。
- None 没有整页图像预热；布局已预排，但显示时仍调用正文绘制。整页位图缓存目前仅 Curl 使用。没有页面位图不代表文字逐个出现在面板上。
- 页脚每 30 秒整 View invalidate，章节段评统计到达后也整 View invalidate；这些是额外刷新源，但不是每次同章翻页都会触发。

## 番茄 APK 的 REA 证据

目标 `fanqie-novel-official.apk`，包名 com.dragon.read，7.3.9.32 / 73932，SHA256 ab70ed786ae97d93ddfac9efd8d48dac8ee27a05eab5d16fbcecbd9033a31d23。方法原文和 Evidence ID 见 `../research/eink-page-turn/rea-evidence.json`。

- `lo7.s.j(MotionEvent)` 明确日志“无动画翻页”，模式值 5；按位移超过屏宽 1/3 或系统最小 fling 速度判定，抬手后发出上一页/下一页事件。
- `FramePager.s1(boolean, lo7.v)` 明确排除模式 5 的动画分支；直接进入 controller 导航，并调用 I1。检查的分支没有 alpha 淡入淡出、sleep 或渐变换字。
- `lo7.s.k` 的模式 5 拖动分支主要记录方向 `b(x)`，区别于覆盖/平移分支移动页面并刷新；方法存在 JADX 代码重复警告，不能据此断言整个调用链完全没有 invalidate。
- `DefaultFrameController.O1` 调用 `J2(1,2,0)` 轮换三个页面 View，再 K2 更新；`J2` 原文确认对页面 View 数组进行置换。这与阅读狗单 View 改 Frame 后重绘结构不同。
- `cn7.c.onDraw` 在页面 D0() 提供有效 Bitmap 且目标矩形非空时先 drawBitmap，随后调用 drawHelper.w 继续绘制。位图路径是条件性的，不能说番茄始终仅使用一张完整位图，也不能将它认定为无动画专属优化。
- 本轮所查路径未建立厂商墨水屏 API 调用；没有全 APK 扫描或墨水屏设备执行证据，不能声称番茄不存在设备适配。

## 结论与可验证改进

确认有实现差异；没有证据证明番茄无动画暗含柔和过渡。优先改进候选是消除 NONE 拖动的无效重绘、合并提交的刷新入口，以及将页脚/段评更新限定到必要区域。整页图像准备可作为对照实验，不能预先保证改善墨水屏观感。

若实施，分别计数 invalidate、onDraw、实际呈现帧，确保拖动不重绘、提交无中间空白，保留取消手势和原文偏移；再在用户墨水屏上进行同书同字号 A/B 比较。现有小米 6 回归只能证明功能与合成画面，无法验收墨水屏物理刷新。
