# 阅读狗私有后端与版本发布

v0.30 起小说内容与图片由手机直连番茄；同步、检查更新、APK 与校验文件继续使用 `https://reader.fadeaway.ltd`，不再创建 GitHub Release，也不向旧备用站上传。既有历史 Release 和下载站保留为历史资料。

首次迁移需要手动覆盖安装 v0.24（保留原签名及数据），旧版应用不会自动发现新的私有更新源。之后进入「关于阅读狗 → 检查更新」。

## 构建 Android

保留原来的 `android-replica/signing/debug.keystore`。私有配置 `android-replica/assets/backend-private.json` 包含 `{"token":"与后端 ACCESS_TOKEN 相同的私有令牌"}`，已被 Git 忽略；不要提交或公开带令牌的自用 APK。

```powershell
./android-replica/build-touch.ps1
```

产物 `android-replica/touch-build/yuejie.apk`。脚本从 PATH 选择 JDK，使用 `C:/Android/Sdk`。新开发机需先配置 JDK 17 和 Android SDK。

## 私有发布

发布任务必须同时完成 GitHub 源码提交与推送，完成条件见根目录 [AGENTS.md](../AGENTS.md) 的版本发布规则。

先递增 `android-replica/version.json` 的 name/code，并更新 `RELEASE_NOTES.md` 顶部说明。构建和测试通过后：

```powershell
python -m pip install paramiko
python deploy/publish-release.py --password
```

密码通过交互输入，不写入发布脚本。SSH 主机指纹必须事先核验并存在于本机 known_hosts；脚本不会自动接受新主机。未新增任何长期 root 发布密钥。

需要在本机复用连接时，可运行 `python deploy/server_connection.py --host <服务器地址>`，通过隐藏输入保存密码。配置位于 `%LOCALAPPDATA%/CodexServerConnections/reading-dog.json`，密码使用 Windows 当前用户 DPAPI 加密；仅同一 Windows 用户可解密。不要将此本机配置提交到仓库。之后发布可使用 `python deploy/publish-release.py --saved-connection`，仍验证 known_hosts，且保存配置的主机必须与 `--host` 一致。其他本机项目可复用 `deploy/server_connection.py` 的 `load()` 接口，不需要明文配置。

若已有获授权的 SSH 发布身份，也可使用 `--key <已有密钥>`；该身份应强制运行 `/usr/bin/python3 /opt/reading-dog-backend/receive-release.py`。此项目不会自动配置 SSH 授权。

发布脚本校验包名、版本、原签名指纹和 SHA-256，再上传版本包；接收器校验大小、元数据、校验文件和 ZIP 完整性。每个版本不可替换成不同 APK，禁止回退最新 versionCode；版本目录先完整落盘，再原子更新 latest.json。上传后脚本通过 HTTPS 下载实际 APK 再次核对。发布脚本本身不执行 Git commit 或 Git push；执行发布任务时需另行提交本次版本对应的源码、版本号、发布说明、测试及文档，并推送到 GitHub 目标分支，核对远端提交后才算发布完成。

仅生成本地发布包：`python deploy/publish-release.py --prepare-only`。

## 后端运维

服务器目录 `/opt/reading-dog-backend`，独立 Compose 项目 `reading-dog-backend`。后端端口仅绑定 `127.0.0.1:18082`，由现有 `vpn-next-gateway` 转发阅读域名。网关配置变更先备份、运行 `nginx -t`，再平滑重载。共享现有通配符证书及其自动续期，不修改 VPN 路由。

```sh
cd /opt/reading-dog-backend
docker compose ps
docker compose logs --tail 100
curl -fsS http://127.0.0.1:18082/health
```

`.env` 存放 `ACCESS_TOKEN`，权限 0600。`data` 由 UID/GID 10001 持有，保存书库 SQLite、匿名上游身份、版本文件及备份；升级镜像必须保留此目录。备份每 24 小时生成，首次启动后约 1 分钟检查，每天最多一份 SQLite 一致性快照，保留 7 份。

v0.27 可选字体保存在 `data/fonts`，需从源码 `backend/fonts` 部署两套 TTF 及 catalog.json，文件权限 0644。字体路径无需认证，可直接独立下载；APK 内的 font-catalog.json 固定了相同大小和 SHA-256。更换字体二进制时必须同时更新目录并发布新 APK，不能只替换服务端文件导致旧客户端校验失败。Android 正式 assets 中不得放回 TTF。

恢复时先停新后端，备份整个 data，再将选定快照复制为 library.db，移开旧 WAL/SHM 并确认目录所有权后启动。快照之后的已确认同步可能丢失；不要将备份视为无损回滚。上游身份、版本文件及 `.env` 需另做受保护备份。

后端源码、构建和接口说明见 `backend/README.md`。旧 `download-site` 文件仅用于历史下载站，不参与新版本发布。
