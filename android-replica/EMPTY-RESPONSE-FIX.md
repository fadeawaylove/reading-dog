# 正文空响应修复与底部导航精简

## 复现与原因

小米 6 真机日志和独立 instrumentation 均复现正文 `batch_full/v` 返回 HTTP 200、零字节正文。最小复现为读取未缓存章节：旧匿名配置成功完成 registerkey，随后正文为空。重新初始化内容配置后，同一章返回 2522 字符。这证明仅重新注册会话密钥不足以恢复本次故障；服务端使旧配置失效的具体原因不作推断。

原实现只在搜索首页处理匿名配置失效，正文缺少恢复路径。

## 修改

- 正文和密钥注册遇到空响应时，重新初始化内容匿名配置、绑定新的会话密钥，并重试一次。
- 恢复有 60 秒间隔，持续失败不会无限初始化或请求。
- 多个阅读客户端通过共享配置锁复用已经更新的身份；旧客户端检测身份变化后重新注册密钥，避免使用旧身份的密钥。
- 搜索配置、书架、进度和正文缓存保持原有数据；缓存命中不触发初始化。
- 移除底部导航图标下的“发现”“书架”及英文细字，保留图标、中央阅读狗字样、点击区域与无障碍名称。

## 验证

已使用既有签名 root 覆盖安装，在小米 6/Android 9 上完成：

- [受控回归结果](tests/empty-response-fix/recovery-results.json)：正文空响应恢复、旧客户端密钥重新绑定、持久空响应重试上限/冷却、registerkey 空响应恢复全部通过，同时通过 22 个协议向量和原有隔离存储检查。
- [真实接口结果](tests/empty-response-fix/probe-results.json)：恢复曾失败的原始内容配置，读取《穿越乱世：每天一份拼好饭》第 3 章未缓存正文，自动更新配置后得到 2724 字符，并在原生阅读页显示。
- [导航结果](tests/empty-response-fix/navigation-results.json)：可见小字已移除，图标导航及无障碍名称保留。
- [正文截图](tests/empty-response-fix/content-recovered.png) / [导航截图](tests/empty-response-fix/navigation-no-captions.png)。

复现命令：安装正式/测试 APK 后，通过 root 执行：

```powershell
$readerAdb = 'C:\Users\admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $readerAdb shell su -c 'am instrument -w -e phase recovery com.example.yuejie.tests/com.example.yuejie.DeviceChecks'
& $readerAdb shell su -c 'am instrument -w -e phase probe com.example.yuejie.tests/com.example.yuejie.DeviceChecks'
& $readerAdb shell su -c 'am instrument -w -e phase nav com.example.yuejie.tests/com.example.yuejie.DeviceChecks'
```

受控测试使用隔离数据库/配置与虚拟传输；真实接口测试使用手机当前书架内的未缓存章节，两者证据分开。未将测试中的匿名配置备份导出到本交付目录。
