# 0.7：修复段评导致正文偏左

> 最新为下方 0.8 补充修复。0.7 仅验证了排版框，未覆盖完整汉字列的实际字形中心，不能以其通过记录声称视觉问题已经完全解决。

## 原因与参考

0.6 为段评在正文右侧额外预留了 28dp，但左侧仍为 20dp，实际左右边距是 20/48dp，正文区域中心向左偏 14dp。`AlignmentChecks` 在修改前实际运行失败，见 [失败记录](tests/text-alignment/red-results.json)。这属于段评布局引入的问题。

已直接查看小米 6 安装的番茄阅读页：[参考截图](tests/text-alignment/fanqie-reference.png)。番茄的段评气泡跟在段落最后几个字后面，正文使用完整的对称宽度，不为气泡空出右侧整列。

## 修复

- 正文和章节标题均恢复左右各 20dp；开关段评不会改变正文行宽或行内换行位置。
- 气泡跟随最后一行文字，留 6dp 间距；当前行放不下时，放到段落下方，不压在正文上。分页提前计算下方气泡所需高度，避免截在页底或碰到下一段。
- 绘制与触摸共用气泡的实际矩形，触摸范围为至少 44×44dp；不再把右侧整列当作段评入口。
- 视觉缩进和气泡不写入原文；长段跨页仍只在段落结束处出现气泡。阅读位置继续保存章节 ID 和原文字符偏移。
- 字体、字号、阅读偏好、四种翻页方式及接口不作本轮调整。

## 实测

小米 6，Android 9 / API 28，root 覆盖安装 0.7 / versionCode 7。

- [对齐与几何回归](tests/text-alignment/reader-align-evidence/alignment-results.json)：左右 20/20dp，偏移 0。45 组字号、行距、段落与 Unicode 组合通过，覆盖同行气泡和下方气泡；验证正文行宽、行末字符边界、连续分页、气泡不遮字、不超出页底、不碰下一段。
- [段评真机回归 23 项](tests/text-alignment/reader-align-evidence/comments-results.json)：实际点击段尾气泡、评论分页/重试、配图预览、返回层级、日夜主题、非零偏移下重排、实际滑动、跨章、快速换段与后续 5 章缓存全部通过。
- [数据对比](tests/text-alignment/reader-align-evidence/data-preservation.json)：覆盖安装和回归前后，3 本书、书架最近阅读时间、所有阅读位置和书签一致。随后打开用户原来的《穿越乱世：每天一份拼好饭》第609章，按原文位置恢复，查看同章前后截图。

[修复前](tests/text-alignment/before.png) · [修复后，同章同字号](tests/text-alignment/after.png) · [其他段落的同行气泡](tests/text-alignment/reader-align-evidence/inline-bubbles.png)

## 交付

[reading-dog-0.7.apk](releases/reading-dog-0.7.apk)，同时更新 `build/yuejie.apk`。

SHA-256：`e1c99269156b233a668189342b95335d4b07cd4603010a643457c5bed6b1f0de`。

沿用原包名与证书，测试 APK 已移除。修复用例源码为 `tests/AlignmentChecks.java`，入口 `com.example.yuejie.commentchecks/com.example.yuejie.AlignmentChecks`。

## 0.8：按实际汉字列宽居中

用户再次反馈后，测量当前章节截图：在 22sp 下，满行的实际字形左侧约 63–65 像素，右侧约 99–100 像素，仍向左偏约 18 像素（6dp）。排版框 960 像素宽，但 14 个汉字实际占用 924 像素；剩下的 36 像素留在行尾。只检查排版框左右边距不足以发现此问题。

修复使用当前字体的 `measureText("汉")` 测量字宽，在至少 20dp 的边距内计算完整汉字列宽，然后将剩余宽度平均分到两侧。当前 22sp 对应正文框左右各 26dp；段落继续正常左对齐、首行缩进两字，气泡继续跟随段尾。阅读位置以原文偏移恢复。

- [像素断言失败记录](tests/text-alignment/glyph-red-results.json)：0.7 的框对称检查和 45 组几何检查通过，但实际字形像素断言失败，证明原测试存在盲点。
- [0.8 验收结果](tests/text-alignment/glyph-center-evidence/results.json)：45 组几何/连续分页/气泡检查、30 组实际字形位图检查通过。位图组合为 5 个字号、黑体/宋体与 720/1080/1440 像素宽度，均在同一小米 6 上构造与绘制；这些宽度组合不是三台物理设备实测。
- 22sp 的同字满行测试，实际像素边缘左右均为 81，偏移为 0。
- 用户当前《穿越乱世：每天一份拼好饭》第610章，22sp、平移翻页，第2页、原文偏移117：实际向前/向后滑动正常，未误开段评；测试后恢复原始阅读偏移。
- [实际章节截图像素测量](tests/text-alignment/glyph-center-evidence/screenshot-ink-measurements.json)：两条填满的文字行由约17.5像素偏左降到0.5–1像素；标点形状与段落末尾短行本身不要求每行字形包围框相等。
- [数据保留](tests/text-alignment/glyph-center-evidence/data-preservation.json)：3本书及最近阅读时间、所有原文位置和书签在覆盖安装/测试前后一致，用户阅读设置保留。

[0.7 用户反馈时的截图](tests/text-alignment/current-followup.png) · [0.8 当前章节截图](tests/text-alignment/glyph-center-evidence/current-user-fixed.png)

已 root 覆盖安装 [reading-dog-0.8.apk](releases/reading-dog-0.8.apk)，同步更新 `build/yuejie.apk`；SHA-256 `bf670c038cd034babc2d2ccc024211b77736bb39f1498de2865ff66f428d0e7a`。测试包已移除，正式 App 恢复到用户正在阅读的书籍。
