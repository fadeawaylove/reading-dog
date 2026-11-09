# 阅读狗独立后端

Java 17 HTTP 服务，承接小说接口、图片、单人多设备同步和自有更新下载。Android 保留本地 SQLite、排版、阅读设置、书签及离线缓存。无注册登录界面，业务 API 使用 APK 内注入的私有 Bearer token；`/health` 无需认证，手机首次安装使用单独的私有下载链接。

## 构建与启动

```powershell
./backend/build.ps1
```

固定依赖：org.json 20240303、sqlite-jdbc 3.45.3.0、jsoup 1.17.2、slf4j 2.0.13。下载到忽略的 build/lib；构建提取 Linux x86_64 SQLite 原生库，避免只读容器运行时解压失败。本部署面向目标机器的 amd64 架构。

部署 Dockerfile、compose.yaml、assets 和 build 产物到 `/opt/reading-dog-backend`，配置 `.env` 的 `ACCESS_TOKEN`（至少 32 字符）。创建 UID/GID 10001 可写的 data 后 `docker compose up -d --build`。镜像仅含 JRE 和编译产物，运行用户无 root 权限、根文件系统只读、内存上限 256 MiB。

## 接口

- `GET /api/v1/time`：服务器时间。
- `POST /api/v1/search`：keyword、offset、search_id、passback；返回 books 和分页游标。
- `POST /api/v1/detail`、`directory`、`chapter`、`page-metadata`：book / chapter 字符串 ID；directory 支持 force。
- chapter 在原有字段之外返回 `image_version: 1` 与 `images: [{offset, url, width, height}]`。offset 为 `text` 中 U+FFFC 的 UTF-16 下标；width/height 未知时为 0。正文、段落文本和段评偏移保持原语义，图片地址仍通过 media 代理访问；旧客户端可忽略新增字段。
- `POST /api/v1/comments/metadata`、`stats`、`page`：服务端验证正文版本与段落映射，返回 Android 所需结构。
- `GET /api/v1/media?url=…`：仅允许指定上游图片域名、HTTPS 和图片类型；限制重定向、内网地址与响应大小，不是通用代理。
- `POST /api/v1/sync`：cursor 与 operations；返回 ack、changes、cursor、more、server_time。小说接口成功返回 `{data: …}`，同步和时间返回自己的对象；错误为 `{error: …}` 并带相应非 200 状态。
- `GET /api/v1/updates/latest`：私有版本元数据。`GET /releases/<version>/<file>`：不可变 APK / SHA256SUMS.txt。
- `GET/HEAD /install/<随机凭据>/reading-dog-<version>.apk`：手机浏览器首次安装入口，只能下载授权版本，不能访问同步或其他 API。凭据仅以 SHA-256 哈希保存于 data/install-links.json；移除对应条目即可撤销链接。
- `GET/HEAD /fonts/reader-body.ttf`、`/fonts/reader-serif.ttf`、`/fonts/catalog.json`：用户主动下载的可选字体及校验目录，无需认证。只允许这三份文件，存储于 data/fonts。源码字体保存在 backend/fonts，部署时复制到持久目录，权限允许服务 UID 10001 读取；不打入 Android 正式 APK。

生成扫码下载链接：在服务器运行 `python3 /opt/reading-dog-backend/create-install-link.py 0.24`。该命令只为已发布 APK 创建链接，不改变应用版本或主 API 访问凭据；无需重建 APK。链接和二维码不进入公开仓库。

上游签名、身份注册、密钥与重试从原 Android Java 实现迁移。`ProtocolCrypto` 的源代码保留在两端以兼容历史测试；Android 业务不再运行上游协议。服务端持久化身份与密钥绑定策略，最多一次空响应重新初始化，保留冷却间隔。

## 同步语义

按 shelf / progress 分别维护实体，所有 ID 为字符串。操作含 UUID、book、kind、at 及 value；服务器事务内去重，以事件时间和 UUID 确定冲突胜者。删除保留墓碑，读书不会自动把书加回书架。正在阅读时远端进度暂存并提供跳转提示，用户新翻页优先；离开阅读后采用仍待确认的远端位置。最近阅读顺序使用进度事件时间。

客户端写本地数据和 outbox 在同一 SQLite 事务；相同实体合并待发送值，确认按操作 ID 删除，旧请求返回不会误删新操作。服务器分页返回当前实体版本，游标与本地合并同事务提交。首次同步按历史时间合并已有书架/进度；先校准服务器时间，禁止首批导入覆盖已存在的新版本。

v0.25 客户端使用服务器时间与单调时钟锚点，队列额外记录系统启动标识和事件 elapsedRealtime。每次校准可修复未确认操作时间；重定时分配新操作 ID，迟到确认不会删除新值。旧版未来时间队列通过 SQLite v4 迁移自动恢复。阅读器恢复/重建时检查已持久化的远端进度，接受位置不会自动重新上传旧页面。

启动/前台/网络恢复触发同步；阅读变化 5 秒合并、退出立即尝试、前台约 30 秒拉取。后台不常驻，系统强停后下次启动补传。书签、字号等设备偏好与缓存不跨设备同步。

## 验证

v0.28 段评使用独立上游身份和锁，`/api/v1/comments/` 经单独单并发、有界优先级队列处理，避免慢段评占用正文 HTTP 工作线程。`X-Reader-Priority` 区分主动查看、下一页及附近预取；正文和目录有独立请求通道。客户端缓存及验证边界见 [段评提前缓存](../android-replica/COMMENT-CONTENT-PREFETCH-v0.28.md)。

```powershell
./backend/test.ps1
# 本机保留原生向量时：
./backend/test.ps1 -NativeVectors
```

本地保留原生协议向量时，可运行 Android tests/ProtocolCheck.java 对照本后端类路径；TextParityVectors.java 生成 6 章正文的本地段落夹具，再通过 `android-replica/tests/build-sync.ps1` 验证 Android 文本完全一致。向量、解密正文及设备书库不入 Git。

测试 APK 采用两个隔离 Context/SQLite 书库，不把测试书籍同步进个人生产书库；旧版 SQLite 升级、outbox 重开、迟到确认、删书/重加、远端进度暂存、HTTPS 和更新均有专项检查。原 Android 身份恢复、密钥绑定及段落范围校验迁至 BackendRecoveryChecks / BackendChecks，Android 历史诊断改为使用后端规范化结果；历史固定书籍仍可能受上游目录及可用性变化影响。
