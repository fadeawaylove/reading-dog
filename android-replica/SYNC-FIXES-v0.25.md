# v0.25 同步回归修复

修复本次审查的三项问题：

- ReaderActivity 恢复时同时检查 deferred 与持久化 progress，提示其他设备的新位置。接受远端位置时不调用旧页面保存逻辑；重建 Activity 恢复 UI 锚点时也不将旧位置当作新的阅读操作上传。
- SyncEngine 比较现有任务的剩余延迟，5 秒写入任务可以提前 30 秒轮询；更晚请求不会推迟已有任务。
- 新增 SyncClock：服务器时间锚点随 elapsedRealtime 推进，同一系统启动内不受用户调整墙上时间影响。队列保存启动标识及单调事件时间；每轮校时事务内修正失准的未确认操作及对应实体时间。重定时使用新 UUID，迟到的旧确认不会删除修正后的操作。旧版缺少时间元数据的未来操作也能恢复。

SQLite v3→v4 只为 sync_outbox 增加 event_boot/event_elapsed，保留所有既有数据与队列。旧 v2 数据迁移仍可运行。

## 验证（2026-11-09，Asia/Shanghai）

使用 Android 35 隔离模拟器及实际 SQLite / ReaderActivity 路径。修复前 SyncRegressionChecks 的三个原始场景全部失败：30 秒任务未提前、未来操作未修复、恢复阅读器未提示。修复后同一测试通过，并扩展到 Activity 重建、v0.24 队列迁移、重定时旧确认与历史操作保留。

- SyncRegressionChecks：18 项断言通过，4 个场景全部通过。
- SyncChecks：19 项检查通过，包含 612 段原生正文文本/偏移一致性。
- UpdateChecks：28 项离线夹具检查通过；32 项含实际 HTTPS 元数据与 APK 下载的更新检查通过。
- 主应用构建、原签名验证、所有顶层 Java 测试源码编译、git diff --check 通过。
- 发布版本 0.25 / code 25，仅发布到私有后端，线上 latest 已核对。
- APK SHA-256：`4bfadc3cbd1d0cc5b7a04b1e79a3bd668df8600648bc1dffbc508354458046bb`。

未连接真实手机；没有对个人生产书架写入测试数据，模拟器生命周期夹具使用本地缓存及替代同步传输。物理设备接力阅读仍需实际使用确认。

复现命令（仅隔离模拟器，不能在含个人数据的真实设备运行回归夹具）：

```powershell
./android-replica/build-touch.ps1
./android-replica/tests/build-sync.ps1
adb -s emulator-5556 install -r android-replica/touch-build/yuejie.apk
adb -s emulator-5556 install -r android-replica/tests/sync-build/checks.apk
adb -s emulator-5556 shell am instrument -w com.example.yuejie.syncchecks/com.example.yuejie.SyncRegressionChecks
```
