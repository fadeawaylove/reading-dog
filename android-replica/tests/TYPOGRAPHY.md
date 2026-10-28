# 番茄整页排版差分检查

参考：小米 6 / Android 9 / 1080×1920 / density=scaledDensity=3，番茄 7.3.9.32。只使用真机。

`typography-goldens.zip` 内含 index.json 和 105 份独立原生布局记录。每份保留字体、运行时配置、原文、段落范围、分页范围、逐行范围、逐字矩形和段评 delegate。`nativeFields` 保留 TTText 的 Unicode scalar 偏移；检查使用转换后的 UTF-16 偏移。不要通过运行 ReaderPaginator 来重新生成预期值。

样本组成：

- 78 份实际可读章节：两种字体 × 17–29 号 × 三档行距，同一完整第 209 章。
- 3 份悠宋 24 号间距重复采集，用于稳定性核对。
- 12 份同一原生引擎的受控正文回放：两种字体 × 16/30 号 × 三档行距，含标点、中英数字、组合字符、补充平面字及 emoji。
- 12 份同一原生引擎的受控标题回放：两种字体 × 16/30 号 × 三档行距，使用真实章节标题。

16、30 号不是番茄字号选择器的可选值，因此这两档明确属于原生受控回放，不能描述成 UI 直接选择的实际章节样本。回放使用实际资源回调和有效布局回调，不修改书籍或偏好。

运行：

```powershell
powershell -ExecutionPolicy Bypass -File android-replica/tests/build-typography.ps1
adb install -r android-replica/tests/typography-build/checks.apk
adb shell am instrument -w com.example.yuejie.typographychecks/com.example.yuejie.TypographyChecks
```

MIUI 拦截安装时，本次用 adb push 后 root `pm install -r`，未使用模拟器。输出位于独立包 files/typography.json 和 files/typography-<fixture>.json。测试不会打开主 App 的书架或偏好。字体、断词规则和图标从生产 assets 复制，避免参考与生产资源版本分叉。

`TypographyPreviewActivity` 仅在测试包声明，用生产分页器和 ReaderSurface 绘制捕获文本。`TypographyIntegrationChecks` 是另外的 instrumentation 包，验证主 App 中原阅读位置、字号重排、前后翻页、段评命中和内容加载，并恢复设置与进度。它不属于生产 APK。

排版参考比较默认字重；用户自定义加粗档位未做番茄逐档映射验收。字号随系统缩放变化、其他设备尺寸、其他字体及更多章节也不属于本次已采集证据。
