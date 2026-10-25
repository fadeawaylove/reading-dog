# 段评数字与段尾位置调整

2026-10-25，参考手机上的番茄阅读页与官方 APK 静态代码。

段评改为空心细线对话气泡，带左下尾巴、加粗小数字，按正文字号分三档；普通计数使用灰色，热评按 APK 默认 10000 阈值使用橙色。计数遵循普通数字、999+、一位小数万、整数万、百万的规则。番茄服务端可调整热评阈值和章节启用条件；本实现采用已核对的默认阈值，没有复制其服务端实验配置。

位置跟随段末最后一个字的实际水平坐标，避免首行缩进被重复加到气泡间距。垂直按文字字体度量定位，不随额外行距向下偏。段尾通过最后一个 Unicode 码点的 ReplacementSpan 为气泡预留宽度；空间不足时末字与气泡一起换行。取消原先右下方额外增加 24dp 段高的逻辑，不再为整页预留右侧段评列。跨页段落仅结束片段显示入口，点击仍映射原文段落起点。预留宽度不加入原文，进度仍使用原始字符偏移。

参考代码：`com.dragon.community.impl.reader.entrance.b.h/l/k` 定位、居中和绘制；`ts2.f.a` 字号分档；`com.dragon.community.impl.reader.u.h` 气泡预占位；`ti2.g.b` 计数格式；`ParaCommentConfig.hotCommentMinCount` 默认热评阈值。REA 返回的部分反编译控制流存在警告，只采用可核对的数据字段和定位公式；并保留真机截图作为独立证据。

- [参考源码记录](tests/comment-style/final/reference-code.json)
- [参考默认热评配置](tests/comment-style/final/ParaCommentConfig.java)
- [恢复后的番茄参考页](tests/comment-style/final/reference-restored.png)

验证设备为同一台小米 6 / Android 9，没有使用模拟器。

- [最终排版结果](tests/comment-style/final/approved-alignment/results.json)：108 组字号/行距/段落组合，144 个段尾气泡，12 个占位换行情形；检查原文连续分页、每段仅一个结束气泡、页底/下一段边界，以及实际正文像素没有被气泡覆盖。另有 30 组实际字形居中检查及计数格式边界检查。
- [最终段评回归](tests/comment-style/final/approved-comments/comments-results.json)：23 项通过，包含真实接口、气泡点击、分页、图片、日夜主题、开关重排、滑动、跨章、快速切段和后续五章缓存。
- [日间效果](tests/comment-style/final/approved-comments/comments-reading-day.png) · [夜间效果](tests/comment-style/final/approved-comments/comments-reading-night.png)
- [数据保留](tests/comment-style/final/data-preservation.json)：保留 3 本书、书架顺序/最近阅读时间、全部进度和书签。测试生命周期曾重新保存页首偏移，收尾时从本轮基线恢复进度与最近阅读时间，保留新获取的正文缓存；恢复数据库 SHA-256 与真机一致。阅读偏移851保留，重排后页码可能改变。

此前并发操作导致的视觉测试中断及权限失败不作为成功证据。最终专项测试已独占真机通过。番茄原字号24与原参考段落已恢复，测试包已卸载。轮廓为本项目 Canvas 绘制，检查结果不代表番茄所有字体、服务端实验或设备上的逐像素一致。

交付 APK：[reading-dog-comment-style.apk](releases/reading-dog-comment-style.apk)，同步 `build/yuejie.apk` 并已覆盖安装；沿用 0.10 包版本和原签名，保留翻页和电池图标改动。

SHA-256：`5e89a88f68cb1fbce46c05c6436def07cf884530be9bd43db2c2e8b3efc0e859`。

独立构建：`build-comment-style.ps1`；专项测试构建：`tests/build-comment-style.ps1`。
