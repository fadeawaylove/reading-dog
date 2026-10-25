# 阅读页目录排序

阅读菜单 → 目录，标题栏提供“倒序 / 正序”切换。按钮表示点击后的排序方式，与真机番茄目录行为一致。切换后从列表顶部展示，方便倒序查看最新章节；平时重新打开目录仍定位当前阅读章。排序偏好通过 `catalog_descending` 保存，默认正序。

排序仅转换列表位置，不反转原始目录。条目渲染、当前章高亮、点击跳转和当前章定位都使用同一转换；阅读进度、上下章及书签继续使用原始章节顺序。书签页不显示章节排序按钮。

## 2026-10-25 真机验证

- 已截图核对番茄正序、倒序目录及切换按钮。
- 小米 6 / Android 9，`am instrument -w -e phase catalog-sort com.example.yuejie.commentchecks/com.example.yuejie.CommentChecks`：5 项通过。
- 遍历真实目录的全部 484 条倒序映射，确认原始目录未修改、切换不改变阅读章和字符偏移、偏好可重新读取。
- 验证倒序点击打开正确章节、切回正序、书签页独立。
- 测试恢复原阅读位置和排序偏好，原签名覆盖安装，未清除用户数据，测试包已卸载。
- 截图与结果：`../research/catalog-sort-ascending.png`、`catalog-sort-descending.png`、`catalog-sort-results.json`。
- APK `build/yuejie.apk` SHA-256：`89d278ea31adcf980d696d46d935ebbcde8c377a71f0368c41d4660638213c06`。包含此前段评末尾换行修复。
