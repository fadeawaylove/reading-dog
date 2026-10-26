# 发布时双份上传

GitHub 是正式发布地址，服务器只是备用下载。服务器不轮询 GitHub。App 优先使用 GitHub，连接超时或读取停滞 5 秒后尝试备用站；检查更新与校验文件请求也支持网络失败后的备用请求。正常持续下载不会因总耗时超过 5 秒而中断。

备用入口：http://magpie.fadeaway.ltd/reading-dog/

## 每次发布

按照现有流程更新版本、构建并验证签名，准备 `main`、`v<版本>` 标签和包含同一签名 APK 的 `release-assets/v<版本>` 分支。然后从仓库根目录运行：

```powershell
python deploy/publish-release.py
```

此命令原子推送三个 Git 引用，等待标签对应的 GitHub Actions 成功及正式 Release 发布，下载核对 GitHub APK 与校验文件，再将**本地同一 APK**通过受限 SSH 上传到备用服务器，最后下载备用包核对 SHA-256。任一目标验证失败，命令以失败退出，不报告发布完成。

已推送或备用上传失败后重试（不会重复创建 Release）：

```powershell
python deploy/publish-release.py --skip-push
```

当前机器的默认上传密钥在已忽略的 `root-work/download-hosting/publish-key`，也可用 `--key <路径>` 指定。密钥不进入版本库。新机器需预先核验并配置 SSH 主机指纹及受限上传密钥。仅在 GitHub 推送标签不会自动上传备用站；完整发布必须使用上述本地入口。

## 服务器

独立服务 `reading-dog-download.service` 使用 `/opt/reading-dog-download/nginx.conf`，HTTP 80 端口，静态目录 `/srv/reading-dog-download`。只提供 GET/HEAD，禁用目录列表、隐藏文件及符号链接访问，不提供 HTTP 上传。现有其他服务不变。

`reading-dog-download` 账号的发布密钥强制执行 root 所有的 `/opt/reading-dog-download/receive.py`，不能执行任意 SSH 命令、使用终端或转发端口。接收器验证版本、大小及 SHA-256，拒绝替换已发布版本的不同 APK，拒绝回退首页版本。没有同步定时任务。

`download-site/` 保存已部署的 Nginx 配置、服务定义与接收脚本。修改后需另行部署，普通发布仅上传 APK、校验文件和下载页元数据。
