# 阅读页段评

最新数字样式与段尾占位规则见 [COMMENT-BUBBLE-ALIGNMENT.md](COMMENT-BUBBLE-ALIGNMENT.md)。下文 0.6/0.7 的 999+ 上限、额外段高和旧截图为历史记录。

## 使用

默认显示段评。正文右侧段尾显示评论数量，大于 999 显示“999+”；点击打开底部段评面板。长段落跨页时，仅结束页有入口。点击正文左右仍翻页，中间仍打开菜单。

面板展示原文摘要、昵称、头像、文字、点赞数及静态配图，向下浏览或点击“加载更多”分页获取；评论只读。配图点击进入全屏预览；返回键先关闭图片，再关闭面板，不改变原文位置。

阅读菜单“更多”提供显示/隐藏段评和重试加载段评。显示开关持久化，0.7 起正文左右保持各 20dp；气泡跟在段尾，同行放不下时放到下方，并为页底留足空间。重排按原文字符偏移恢复。计数返回时只重绘，不改变分页。

## 接口与对应关系

独立实现见 `ParagraphCommentApi`，原始复现与运行证据见 [段评接口报告](../research/tomato-comments/REPORT.md)。手机直接请求官方接口，无下载器进程、电脑服务或登录依赖。

- `metadata` 获取与当前正文一致的正文版本和服务端 `<p idx>` 标识；老缓存按需补齐，不以段落数组下标猜测服务端编号。
- `stats` 获取当前版本的段评数量。标题索引不显示为正文气泡。
- `comments` 使用已验证的 sort=0，通过字符串 cursor 分页。返回标准化的评论 ID、昵称、头像、文字、图片、点赞数、下一游标和结束标记。
- POST 正文的 MD5 参与 Argus 字段 13；原有搜索/正文 GET 签名调用保持原接口。统计/列表解析拒绝业务错误、空响应与错误原文对应。
- 评论与阅读进度解耦；独立工作队列，不阻塞正文或后续五章预缓存。切章/关闭后忽略旧回调；排队任务不会在退出后继续发起新一轮请求，已经发出的请求可能完成并写入缓存。

## 缓存

单独 SQLite `paragraph-comments.db`，不迁移或清空书架数据库。缓存键区分书籍、章节、版本、段落和游标；统计/已查看评论缓存 30 分钟，过期时可先显示缓存再刷新；断网时已有缓存仍能查看并标明来源。评论 JSON（含版本与段落映射）按最近使用限制为 10 MiB。

头像和缩略配图单独保存于应用缓存目录 `paragraph-images`，上限 30 MiB，内存图片缓存 6 MiB；仅解码静态位图，不播放动图、不抓取原图、不打开服务端跳转链接。图片加载失败显示重试文字。评论列表使用原生 ListView，仅创建屏幕附近的行和图片，避免长列表持续累积位图。

## 验证记录

真机：小米 6 / Android 9 / API 28，沿用原包名及签名，root 覆盖安装。

首轮 17 项通过，包括 22 组原协议向量、5 组原版段评 POST 签名向量、真实统计、分页无重复、配图、错段拒绝、空态、实际气泡点击、面板/图片返回顺序、日夜主题、字号重排和跨页气泡位置。首轮记录见 [initial-comments-results.json](tests/paragraph-comments/initial-comments-results.json)。最终版本的后续验证记录追加于本文件，不能将首轮结果当作后续修改已通过。

本轮空态服务测试使用无评论编号响应；该编号不是本章实际正文段落。书中有评论的气泡只依据真实服务端统计出现；不会插入示例评论或虚构计数。

### 最终版本实测

版本 **0.6 / versionCode 6**。已查看最终日间阅读、含图片的评论列表、夜间评论面板、图片预览及断网缓存截图。保留侧会话已完成的四种翻页方式和目录更新修复。

- [段评专项 23 项通过](tests/paragraph-comments/comments-final/comments-results.json)：新增缓存 TTL/版本隔离/LRU、受控空响应后保留内容、点击重试恢复、ListView 中真实缩略图入口、非零原文偏移下开关段评、实际 OS 滑动不触发气泡、快速换段不串评，以及后续 5 章缓存回归。
- [真实断网 5 项通过](tests/paragraph-comments/comments-final/comments-offline-results.json)：冷启动测试进程前关闭 Wi-Fi，移动数据为关闭状态；故意将已查看的评论页标记过期后，仍显示缓存内容并给出联网重试，配图从磁盘读取，返回位置不变。检查后恢复原缓存时间与 Wi-Fi。
- [数据保留对比](tests/paragraph-comments/comments-final/data-preservation.json)：最终覆盖安装及测试前后，3 本书籍 ID 与 last_read、全部章节/原文偏移/目录位置/总数、全部书签完全相同。测试未把临时阅读位置留给用户。
- 最终评论 JSON 缓存 266888 字节；图片缓存目录约 748 KiB，均低于上限。
- 独立服务端请求确认真实正文段落的末页 has_more=false；所选首章所有正文段落都有评论，因此“真实存在且零评论的段落”未做现场 UI 验证。空列表协议与错误/空态处理已验证，不能将越界编号响应描述成真实无评正文段落。

[日间正文气泡](tests/paragraph-comments/comments-final/comments-reading-day.png) · [含配图的段评](tests/paragraph-comments/comments-final/comments-sheet-with-image.png) · [夜间面板](tests/paragraph-comments/comments-final/comments-sheet-night.png) · [图片预览](tests/paragraph-comments/comments-final/comments-image-preview.png) · [过期缓存断网状态](tests/paragraph-comments/comments-final/comments-offline-sheet.png)

### 安装与复测

交付包：[reading-dog-0.6.apk](releases/reading-dog-0.6.apk)，并同步 `build/yuejie.apk`。SHA-256：`d22a95372c78e7193b514985caa6023ae33efd83824a2d83e4bdf7ca59cd4af0`，与手机安装的 base.apk 一致；原证书 SHA-256 仍为 `3d036c2956aa9369777eb0966aa61ea6c84870a2c87860cd8e8c020ff8990bb8`。

手机路径：`/data/app/com.example.yuejie-g8LaIpyAkg50pTtrS5dvFw==/base.apk`。已移除独立测试包 `com.example.yuejie.commentchecks`，未清除用户数据。

默认构建仍用 `build.ps1`。同目录并发任务期间，`build-comments-app.ps1` 和 `tests/build-comments.ps1` 使用独立构建输出；测试入口为 `com.example.yuejie.commentchecks/com.example.yuejie.CommentChecks`，`-e phase offline` 运行离线用例。离线测试本身不关闭网络，调用者应在真实设备断网后执行并恢复网络。


0.7 已修复右侧预留整列导致的偏左问题，最新布局见 [TEXT-ALIGNMENT-FIX.md](TEXT-ALIGNMENT-FIX.md)。上面的 0.6 截图和 APK 校验信息是历史记录。
