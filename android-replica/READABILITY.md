# 全应用纯黑文字与主页字号

2026-10-27：浅底页面的普通文字、辅助说明、占位提示与选中项统一为 `#000000`。公共文字颜色与绿色背景、滑杆、进度条分开配置；深色按钮及图片预览保留浅色文字，夜间配色保留。目录选中行继续通过背景区分。

主页与内联搜索：最近阅读书名 24sp；书架/搜索结果标题 20sp；列表书名 18sp；搜索框和空状态 16sp；作者、章节、进度、数量、状态、新章提示 14sp；重试/加载更多 16sp。书名最多两行，作者和章节省略显示。最近阅读的新章提示单独一行。

## 验证

- 构建、APK 签名校验及小米 6（`6da02a7`，Android 9）覆盖安装成功，无模拟器。
- 书籍详情与完整目录弹窗补充验证 2 组通过，深色阅读按钮仍为浅色文字。
- 主验证 13 组通过，检查 177 个文字控件，覆盖空书架、长书名、新章提示、搜索结果、系统弹窗、关于页深色按钮、继续阅读、目录跳转、书签与夜间设置。
- 字体放大检查在 Activity 资源配置中使用 1.3 和 1.5 倍 fontScale，检查文字垂直裁切并保存真机截图；未修改全机字体设置。
- 搜索回归使用真实接口并返回 20 本书。段评昵称/点赞/正文的黑字检查使用真实行组件和独立样例数据，不冒充新一轮在线段评接口验收。
- 测试书架使用独立数据库；阅读回归结束后恢复原 progress 表及 last_read 排序。阅读设置改动使用独立 preferences，不覆盖用户设置。
- 原有分页和段评下滑关闭改动保留。本次不变更版本号、不发布 Release。

原始结果和截图保存在忽略目录 `tests/readability-evidence/`，主结果 `results.json`，书籍详情补充结果 `details-results.json`。

## 复现

使用现有 `build.ps1` 构建应用后，执行 `tests/build-readability.ps1`。若应用输出目录不同，用 `-AppBuildDirectory` 指定包含 `classes` 的目录。将应用 APK 及 `tests/readability-build/checks.apk` 覆盖安装到真机。

```powershell
adb -s 6da02a7 shell am instrument -w com.example.yuejie.readabilitychecks/com.example.yuejie.ReadabilityChecks
adb -s 6da02a7 shell am instrument -w -e details true com.example.yuejie.readabilitychecks/com.example.yuejie.ReadabilityChecks
```

测试先通过 launcher 入口进入前台，再打开内部页面，避免 MIUI 后台启动限制。
