# 阅读排版验证已替换

之前以 BREAK_STRATEGY_SIMPLE 为预期、统计“比系统逐行排版更早的断点”为零的检查，不能证明与番茄一致，现已废弃。

生产正文不再依赖 StaticLayout 断行，也不再缩窄为整数汉字列。新的实现、番茄 7.3.9.32 原生参考、真机截图和验证边界见 [整页排版报告](READER-TYPOGRAPHY.md)。

LineBreakChecks 现在复用独立 TTText 参考样本检查；build-linebreak.ps1 和 build-typography.ps1 都从 typography-goldens.zip 准备相同参考数据。预期值不再由 Android 排版器或我们的实现生成。
