# 云服务器部署基线

## 组件

- Nginx：HTTPS、静态前端和 `/api` 反向代理；
- Java 21：运行后端 JAR；
- MySQL 8：仅允许应用服务器和管理网络访问；
- 私有文件目录：后续保存照片、原始成绩单和德育分名单，不由 Nginx 直接公开；
- 数据库与文件每日备份，至少保留 30 天。

## 构建

```powershell
mvn -pl backend clean package
Set-Location frontend
pnpm install --frozen-lockfile
pnpm build
```

若与其他站点共用 80 端口并部署到 `/academic-support/`，构建前端时设置：

```powershell
$env:VITE_BASE_PATH='/academic-support/'
pnpm build
```

产物：`backend/target/academic-support-backend-1.0.0-SNAPSHOT.jar` 和 `frontend/dist`。

## 独立服务器测试部署

当前服务器仅部署本平台时，使用 `deploy/docker-compose.server.yml` 和 `deploy/nginx.conf`，
前端以 `VITE_BASE_PATH=/` 构建，Nginx 直接发布 80 端口，入口为 `http://YOUR_SERVER_IP/`。
旧的 `/academic-support/` 地址由 Nginx 重定向到根路径，不再依赖其他项目的网关。
HTML 不缓存，带版本哈希的静态资源缓存 7 天。

服务器目录为 `/opt/academic-support`，其中 `app/app.jar` 为后端，`web/` 为前端，
`data/private/` 为私有附件，`.env` 为服务器单独生成的随机数据库密码和 JWT 密钥。
数据库及后端均仅接入本项目的 Docker 网络，MySQL 不发布宿主机端口。
启动命令为 `sudo docker compose --env-file .env -f compose.yml up -d`。

重新部署的数据源为本地 MySQL `academic_support` 与 `backend/data/private` 的同次快照，
导入前核对文件引用，导入后核对各表数量和私有附件是否完整。

## 必须配置的生产环境变量

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
CORS_ALLOWED_ORIGINS=https://实际域名
PRIVATE_FILE_ROOT=/srv/academic-support/private
GRADE_MAX_BYTES=20971520
MORAL_MAX_BYTES=20971520
```

`JWT_SECRET` 至少 32 字节且使用随机值。`PRIVATE_FILE_ROOT` 必须是后端进程可读写、Nginx 不可直接访问的持久目录。初次创建系统负责人时临时设置 `BOOTSTRAP_OWNER_LOGIN` 和 `BOOTSTRAP_OWNER_PASSWORD`，创建成功后移除。

私有目录至少包含 `photos/`、`grades/`、`moral/`，需与 MySQL 一同备份。恢复时数据库记录与文件目录必须来自同一备份时点。

## Nginx 要点

- 强制 HTTP 跳转 HTTPS；
- 前端 SPA 未命中静态文件时回退到 `index.html`；
- `/api/` 代理到 `127.0.0.1:8080/api/`；
- 禁止直接映射后续私有文件存储目录；
- 限制请求体大小并与后端 25 MB 上限一致；
- 记录访问日志，但避免记录密码、JWT、成绩文件内容。

## 试运行检查

- 执行后端全量测试、前端测试和生产构建；
- 确认 Flyway 已到 V8，且旧期次、报名、正式帮扶对、成绩分组统计与私有文件仍在；
- 使用学生账号验证无法访问 `/admin/**`、原始成绩单和未发布/已撤回德育文件；
- 在窄屏验证成绩映射、成绩表和文件列表可横向滚动或单列显示；
- 检查 `/api/actuator/health` 为 `UP`，并对数据库与私有文件目录执行一次可恢复备份演练。


## 私有 LHCOS 存储

本地开发 `STORAGE_PROVIDER=local`，生产 `STORAGE_PROVIDER=cos`。生产 Compose 的 `env_file` 读取已有 `/home/ubuntu/.config/academic-support/storage.env`，不复制凭证到发布包。SDK 不指定 StorageClass，符合 [LHCOS 与 COS 的差异](https://intl.cloud.tencent.com/zh/document/product/1103/78314)。桶级 API 不作为 LHCOS 可用能力假设，独立 LightCOS（产品1722）的 CORS 说明不适用于本桶。

`STORAGE_DIRECT_UPLOAD=false`、`STORAGE_DIRECT_READ=false` 为当前验证通过的安全模式：浏览器通过同源授权接口上传和下载，后端通过内网读取私有对象；图片仍在站内弹层显示并缓存 Blob。两个直连开关独立控制，仅当最终来源的 OPTIONS、PUT、GET 和手机预览全部通过后启用。启用前需对实际桶验证 CORS；若预检拒绝或响应缺少 Allow-Origin，保持同源模式。不可据此要求重建密钥或开放桶。

已实现直传协议：GET `/api/storage/uploads/capabilities`，POST `/api/storage/uploads`（PHOTO/GRADE/MORAL 及业务参数、filename、mimeType、size），PUT 返回的 5 分钟签名 URL，POST `/api/storage/uploads/{id}/confirm`。签名绑定临时对象键、PUT、Host、Content-Type、Content-Length；后端重新检查归属、阶段、大小、实际内容并写入独立随机正式键。临时对象从不直接成为业务引用。确认通过数据库行锁保证幂等，失败事务删除新对象；过期临时对象每小时清理，成功结果保留7天用于重试。签名 GET 仅授权接口发放，60秒有效；已发签名在撤回后最多仍有60秒窗口，已下载文件无法收回。需要即时撤回时保持同源模式。

内网验证：新服务器解析桶域名及实际 TLS peer 为169.254.0.47，Java SDK 在默认 Docker bridge 下上传、HEAD、读取、签名GET均成功。当前 PNG 响应 Content-Type=image/png，无 Content-Disposition 和 x-cos-force-download；但无 CORS 放行头。COS 普通桶的默认域名预览规则不直接当作本 LHCOS 的验证结论。

迁移保持 `photos/`、`grades/`、`moral/` 原对象键；同时包含历史快照和软删除记录引用。`tools/storage_admin.py migrate --root ... --manifest ...` 会逐文件 SHA256 上传和下载核验，已存在且不同的对象拒绝覆盖。正式切换前停止旧后端写入，重新获取最新 SQL 和附件，不使用本地开发数据库；核对所有旧表行数、Flyway V8 和引用哈希。旧 MySQL、附件、发布包保留，回退前评估新站写入，避免双站分叉。

## 可恢复的数据库备份

部署 `deploy/backup.sh`、`deploy/storage_admin.py` 至 `/opt/academic-support/tools/`，用 python3 venv 安装 `cos-python-sdk-v5`。脚本从容器内部环境读取数据库密码，使用 `mysqldump --single-transaction` 生成 gzip；先在无网络、无宿主端口的临时 MySQL8 恢复，上传 `backups/mysql/production/` 后下载再次恢复，并核对逐表行数与SHA256。只有通过上述检查才生成验证清单和清理过期备份。临时恢复容器只删除自身创建的容器，旧生产容器不受影响。

安装 service/timer 到 `/etc/systemd/system/` 后 `systemctl enable --now academic-support-backup.timer`；每天 UTC19:00（北京时间次日03:00）执行，失败见 `journalctl -u academic-support-backup.service`。至少保留30天且至少7份有恢复验证清单的备份；本地仅在云端验证后清理7天前的备份。照片/成绩原表/德育文件不参与备份前缀清理。100GB容量与10GB公网下行分开监控，不用公开桶解决额度问题。
