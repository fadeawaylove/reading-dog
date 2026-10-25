# 翻页收尾时点击丢失

2026-10-25。用户反馈动画结束的一瞬间点按不生效。

原实现仅在下一次绘制计算中清除 `settling`。如果新手势的 ACTION_DOWN 先到，`ignoreGesture` 就会丢弃整段手势，连动画结束后到达的 ACTION_UP 也不处理。因此视觉已接近完成时仍有不可点击的窗口。

修复在新 ACTION_DOWN 时先结清上一段收尾，再初始化本次手势。前一次计划提交就提交，计划回弹就回弹；新点击按位置继续处理前后翻页、段评或菜单。移除了整段吞手势的标记。动画途中再次按下也可接管，不排队、不额外提交已取消的触摸。对未完成加载等情况，仍遵守 `Host.canTurn()`。

真机小米 6 / Android 9，无模拟器。

- [修复前失败](tests/touch-timing/red.json)：仿真计时已结束，DOWN 先于末次绘制，UP 在收尾后到达；期望第 4 页，实际第 3 页，仅 1 次提交。
- [修复后 33 项通过](tests/touch-timing/green.json)：12 个三种动画的到期前/后及正反方向边界；12 个四种模式的回弹接管、菜单单次响应、取消触摸；8 个真实 Android 窗口中的系统触摸屏事件点按；1 个书架顺序/最近阅读时间、全部进度及书签前后相等检查。

时序用例使用实际 ReaderSurface、手势控制器、原生 Scroller 与内存章节。确定性用例刻意控制最终绘制与输入的先后顺序；真实窗口用例由系统注入触摸事件。两类证据分别保留，未将内存章节用例描述为用户书籍实测。

初轮系统事件缺少 SOURCE_TOUCHSCREEN，修正测试后重跑；另有一次被其他会话覆盖安装中断。最终在其他会话结束后独占真机通过，以上中断不作成功证据。测试窗口采用系统启动方式以兼容小米后台启动限制，测试包已移除。

改动：`src/com/example/yuejie/ReaderGestureController.java`、`ReaderSurface.java`。回归源码：`tests/TimingTouchChecks.java`。隔离构建：`build-touch.ps1`、`tests/build-timing.ps1`。

已覆盖安装并同步 `build/yuejie.apk`；保留当前段评、翻页、电池和全书页码功能，沿用 0.10 包版本。交付包：[reading-dog-touch-fix.apk](releases/reading-dog-touch-fix.apk)。

SHA-256：`ae9bfa72abd956e5fc18836666be1fcc0af2eb4e6100dc6aaa556c473c9dc12c`。
