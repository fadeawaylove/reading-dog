# 段评末尾换行兼容修复（2026-10-25）

真机当前第二章的第 105 段显示“段落内容与当前正文不一致，请刷新章节”。独立调用相同接口确认：`extra.para_content` 比正文多一个末尾 LF，评论的 `para_src_content`、章节版本和段落编号均正确。旧实现使用字符串完全相等，拒绝了正常响应。

`ParagraphCommentApi.sameParagraph` 仅忽略两端字符串末尾的 CR/LF。正文中的空格、换行、标点及其他字符保持严格比较；书籍、章节、版本及起止段落编号校验继续生效。摘要与评论原文使用同一规则。

## 验证

- 小米 6 / Android 9 真机，原实现通过 `adb shell am instrument -w -e phase bug com.example.yuejie.commentchecks/com.example.yuejie.CommentChecks` 复现 IOException。
- 修复后同一段独立请求返回 7 条真实评论，面板展示 7 条，打开段评不改变字符偏移。额外验证 CRLF 兼容、尾部空格不被忽略、不同正文被拒绝，真实错段请求仍被拒绝。
- 原有完整段评回归以 root 启动 instrumentation，23 项全部通过，涵盖真实分页、配图、重试、日夜、手势、切章、快速换段和后续五章缓存。普通 shell 启动时受到系统 INJECT_EVENTS 权限限制；root 重跑通过。
- 安装前后书籍 ID、全部阅读章节/偏移/目录位置、书签相同。主页刷新更新了部分封面 URL，最近阅读时间有更新。
- 原签名覆盖安装，未清除用户数据；测试包已卸载。
- APK：`build/yuejie.apk`，SHA-256 `53e16562ecfc81c06d9d6487eda12c3c6a3c30fd5b0bae04b3bc1aaa260b22d4`，与真机安装包一致。

原始证据位于 `../research/paragraph-bug-before.png`、`comments-bug-red.json`、`comments-bug-green.json`、`comments-bug-fixed-sheet.png`、`comments-regression.json`。
