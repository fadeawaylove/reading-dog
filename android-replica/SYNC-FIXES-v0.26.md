# v0.26 暂存进度过期修复

阅读器活跃时收到远端位置 A，进程未经过 onPause 就被终止，数据库中的 sync_deferred 仍会保留 A。随后非活跃同步收到较新位置 B，旧实现只应用 progress，导致进入阅读器或 acceptDeferred 时再次使用 A。

修复：LibrarySync.merge 接受较新版本且该书当前不在阅读时，在同一合并事务内应用新进度并删除该书的旧暂存位置。活跃阅读仍暂存最新远端位置，其他书籍的暂存不受影响，旧版本响应继续由已有版本比较拒绝。

## 回归与发布

- 新增 SyncRegressionChecks.staleDeferred，先写入 A，关闭并重开隔离 SQLite 模拟进程终止后的持久状态，再合并 B，验证旧暂存消失、readerPosition 不再提示 A、acceptDeferred 不会覆盖 B。
- 同时覆盖迟到旧响应、其他书籍隔离及活跃阅读时最新暂存仍能确认。
- 修复前该测试在“background merge must remove obsolete deferred A”断言失败；修复后 SyncRegressionChecks 的 28 项断言全部通过。
- SyncChecks 19 项通过，包含 612 段原生正文文本兼容性；UpdateChecks 的 28 项夹具检查通过。主应用和全部顶层 Java 测试源码编译通过。
- 版本 0.26 / code 26，沿用原签名；不变更数据库版本或后端 API。
- APK SHA-256：`e0cf378bac229f0f0498feabccb2b54523c6847d543962fec5d2d65547505a49`。
- 已发布到私有后端并完整下载验证哈希；发布后的 Android 更新专项 32 项通过，包含实际版本检查、APK 下载、校验、最新版状态与安装授权检查。

验证使用 Android 35 隔离模拟器及两份隔离书库，没有向个人生产书架写入夹具数据。真实手机本轮未连接。
