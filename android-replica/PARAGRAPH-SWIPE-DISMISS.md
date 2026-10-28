# 段评到顶后下拉关闭

番茄参考：本地 `fanqie-novel-official.apk`，版本 7.3.9.32，SHA-256 `ab70ed786ae97d93ddfac9efd8d48dac8ee27a05eab5d16fbcecbd9033a31d23`。

REA 静态调用链：

- `ss2.p1` 构造器日志明确为 `KmpParaCommentListDialog`，内容调用 `KmpParaCommentListLayoutKt.a`。
- `KmpCommunityListWrapperKt$CommunityListWrapperEffect$2$1.invokeSuspend` 监听 `LazyListState.i()`，以 `h() == 0 && i() <= 1` 计算到顶状态，再通过 `nt2.a.b(boolean)` 传出。
- `ss2.p1.h.b` 把状态写入段评对话框的 `S`。
- `ss2.p1.dispatchTouchEvent` 在 `S` 为真时允许外层拦截，否则调用 `requestDisallowInterceptTouchEvent(true)`。

对应 REA evidence：`ev_8d74f4bc9670d5c19565c372c139cee8c1584dd5556cb8c0eed41ebcd44093c7`、`ev_e2c5df4aa2efb25ec925558a6092d8274de96124c9bf00002e086c99667a9e7d`、`ev_203b21d9b85247431bff53f888817a0b3146bee66050867a68e8d7ee0770229c`、`ev_9fc964a6f3ab984d568f172a197b7142790338bbf7cd14888704477d60b2d938`、`ev_057a0f8d965f9b67d6af6d59b62f395de3c17e9a4563c143b328d62bcc7cd8ab`。完整返回保存在本地 `research/paragraph-sheet-gesture-rea.json`。

这些是该 APK 的静态证据，不能据此声称其他版本、账号配置或运行时动画参数完全一致。图片浏览器的 `DragDismissLayout` 未作为段评交互的依据。

阅读狗使用 `CommentSheet` 承载原段评面板。所有区域都遵守列表到顶门槛：内容仍能向上滚动时，下拉留给列表；内容到顶后，继续向下拖动才移动面板。松手超过 72 dp 后关闭，短拖或取消时回弹。使用屏幕坐标计算拖动，避免面板位移影响手指距离；关闭仍调用原来的 `dismiss()`，不改动正文位置与网络回调隔离。

真实设备测试：运行 `tests/build-comment-swipe.ps1`，安装正式 APK 和独立测试 APK 后执行：

```powershell
adb shell am instrument -w com.example.yuejie.commentswipechecks/com.example.yuejie.CommentSwipeChecks
```

测试直接打开真实章节和段评，注入系统手势，覆盖上滑滚动、未到顶下拉、标题区到顶门槛、短拖回弹、取消回弹，以及到顶时从内容区和标题区下拉关闭。测试结束恢复原阅读进度和书架最近阅读时间。

2026-10-27 小米 6 真机通过全部 8 项检查，包括连续下拉从列表滚动衔接到弹层关闭。该设备的系统手势注入需要通过已配置的 root 执行 `am instrument`。结果和前后截图保存在本地 `artifacts/comment-swipe/`。已覆盖安装，设备 APK 与本地 APK SHA-256 均为 `94e9cb49a25b8b20ea195118a190bd872c9f3c774e00e8f5b5419dcefc31365f`。本次未发布正式 Release。
