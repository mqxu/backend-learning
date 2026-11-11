# deploy/ 部署资产说明

本目录是《06-ECS部署与运维实操手册》配套的**可复现部署资产**，目标是：一台全新的阿里云 ECS（Alibaba Cloud Linux 3 或 Ubuntu 22.04）按本目录操作后，能跑起「Nginx + systemd + Spring Boot + MySQL + Redis + 两端静态页」的完整链路。

> 这些文件只在**服务器**上执行，不要在本地开发机直接跑 `init-server.sh`（它会安装系统软件、创建用户）。
> 本地只做语法自检与 `DRY_RUN=1` 演练。

## 1. 文件清单

| 文件 | 作用 | 执行位置 |
| --- | --- | --- |
| `init-server.sh` | 初始化 ECS：安装 JDK 21 / MySQL 8 / Redis / Nginx，创建 `campus` 用户与 `/opt/campus-activity`，生成 env、安装 systemd unit 与 Nginx 站点配置，打印后续手工步骤 | 服务器（root） |
| `deploy.sh` | 部署脚本：`start` / `stop` / `restart` / `status`；`restart` 会把当前版本归档到 `releases/`（方式 B 预发目录模式下保留运行中旧 jar）、同步 `web/`、重启 systemd、`nginx -t && reload`、轮询健康检查 | 服务器（root 或可 sudo） |
| `campus-activity.service` | systemd unit：`EnvironmentFile=/etc/campus-activity/env`、`WorkingDirectory=/opt/campus-activity`、`Restart=always`、journal 日志 | 服务器 `/etc/systemd/system/` |
| `nginx/campus-activity.conf` | Nginx 站点：`/api` 与 `/uploads` 反代 8016、`/` 管理后台、`/app/` 小程序 H5、SPA fallback、10m 上传上限、gzip 与静态缓存 | 服务器 `/etc/nginx/conf.d/` |
| `flow/campus-activity-pipeline.yaml` | 云效 Flow YAML 流水线：Codeup 代码源 → JDK21 Maven 构建 + Node 构建两端 → 制品上传（app.jar/web/deploy.sh）→ VMDeploy 主机组部署 | 云效控制台导入 |
| `env.example` | `/etc/campus-activity/env` 模板（Spring 环境变量 + JVM 参数，不含真实密钥） | 服务器 `/etc/campus-activity/env` |

## 2. 一键使用顺序

```bash
# 1) 本地/CI 构建产物（本地构建命令见 06 号手册第 5、6 章；云效流水线则跳过本步）
cd server && mvn -DskipTests package        # -> server/target/campus-activity-server-1.0.0.jar
cd admin  && pnpm install && pnpm run build                     # -> admin/dist
cd app    && pnpm install && VITE_BASE=/app/ pnpm run build:h5    # -> app/dist/build/h5（部署用 /app/ 子路径）

# 2) 把仓库 deploy/ 目录传到服务器（任选其一）
scp -r deploy root@<ECS-IP>:/root/deploy
# 或在服务器上 git clone 本仓库

# 3) 服务器初始化（幂等，可重复执行；JDK 缺失不会中断脚本）
ssh root@<ECS-IP>
sudo bash /root/deploy/init-server.sh

# 4) 按脚本提示完成三件手工事：
#    a. MySQL 安全初始化（root 密码）
#    b. 建库并导入 schema.sql / data.sql（06 号手册第 4 章）
#    c. 编辑 /etc/campus-activity/env（数据库/Redis 密码、JWT/签到密钥、dev-mode=false）

# 5) 放应用并启动
sudo mkdir -p /opt/campus-activity
# 把 app.jar 与 web/（admin、app 两个目录）放到 /opt/campus-activity/
sudo systemctl start campus-activity
curl -s http://127.0.0.1:8016/api/ping      # 期望 {"code":0,"message":"ok",...}

# 6) 安装 Nginx 站点（init-server.sh 已尝试安装，未装则手工）
sudo cp /root/deploy/nginx/campus-activity.conf /etc/nginx/conf.d/
# 编辑 server_name 为真实域名或 _
sudo nginx -t && sudo nginx -s reload
```

云效流水线部署：把 `flow/campus-activity-pipeline.yaml` 导入云效 Flow，替换 4 个占位符后运行；主机部署会自动把制品放到 `/opt/campus-activity/package.tgz` 并执行 `deploy.sh restart`。

## 3. 占位符清单（必须替换）

| 位置 | 占位符 | 替换说明 |
| --- | --- | --- |
| `flow/campus-activity-pipeline.yaml` | `<REPO_NAME>` | 代码库展示名，如 `campus-activity-01` |
| 同上 | `<CODEUP_REPO_URL>` | Codeup 仓库 clone 地址 |
| 同上 | `<SERVICE_CONNECTION_ID>` | 服务连接 ID（Codeup 代码源认证） |
| 同上 | `<MACHINE_GROUP_ID>` | 云效主机组 ID（已接入 ECS） |
| `nginx/campus-activity.conf` | `campus.example.com` | 真实域名；无域名时改 `server_name _;` |
| `nginx/campus-activity.conf` | HTTPS 证书路径（注释块内） | 启用 HTTPS 时替换 `.pem` / `.key` 路径 |
| `env.example` | `CHANGE_ME_*`、`WX_*`、`OSS_*`、`CLOOPEN_*` | 真实密钥；**不要提交回 Git** |
| `init-server.sh` 环境变量 | `REDIS_PASSWORD` / `MYSQL_ROOT_PASSWORD` / `DB_PASSWORD` / `JAVA_TARBALL_URL` | 可选：用环境变量覆盖默认行为 |

## 4. 本地演练方式（不改本机系统）

```bash
# 脚本语法检查
bash -n deploy/init-server.sh
bash -n deploy/deploy.sh

# YAML 语法检查
python3 -c "import yaml; yaml.safe_load(open('deploy/flow/campus-activity-pipeline.yaml'))"

# systemd unit 语法检查（本机有 systemd 时；macOS/WSL 无 systemd 会提示命令不存在，属正常）
systemd-analyze verify deploy/campus-activity.service

# init-server.sh 干跑：只打印将要执行的命令，不需要 root、不改系统
DRY_RUN=1 bash deploy/init-server.sh
```

Nginx 配置无法在本地校验，必须在服务器上执行：

```bash
sudo nginx -t          # 期望 syntax is ok / test is successful
sudo nginx -s reload
```

## 5. 与教学包事实清单的对应关系

| 事实清单约定 | 本目录落点 |
| --- | --- |
| 安装目录 `/opt/campus-activity/`（jar、uploads、web、logs） | `init-server.sh` 建目录；`deploy.sh` 维护 |
| 运行用户 `campus`，环境变量 `/etc/campus-activity/env` | `init-server.sh` 创建；`campus-activity.service` 读取 |
| systemd `campus-activity.service`，`ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/campus-activity/app.jar` | `campus-activity.service` |
| Nginx 站点 `/etc/nginx/conf.d/campus-activity.conf`；`/api`、`/uploads` 反代 127.0.0.1:8016；`/` 后台、`/app/` H5 | `nginx/campus-activity.conf` |
| 部署包 `package.tgz` 根目录：`app.jar`、`web/admin/**`、`web/app/**`、`deploy.sh` | `flow` 流水线 `stage_artifact` + `upload_step` 生成 |
| 主机部署路径 `/opt/campus-activity/package.tgz` | `flow` 流水线 `artifactDownloadPath` |
| 端口 8016、Redis database 11、MySQL 库 `campus_activity` | `env.example`、Nginx 反代、06 号手册 |

## 6. 注意与待验证项

1. **JDK 21**：发行版仓库不一定提供 `java-21-openjdk`，`init-server.sh` 采用「已有则跳过 → 试包管理器 → 试 Temurin tar.gz → 打印手工指引」的降级逻辑，任何一步失败都不会让脚本退出；生产建议固定 JDK 版本并记录在部署单。
2. **MySQL / Redis 版本**：发行版仓库通常为 MySQL 8.0、Redis 6.0/6.2；本项目用到 `SET NX EX`、Hash、Lua，Redis 6.0 即可跑通，建议 6.2+。Redis 的 systemd 服务名在不同发行版是 `redis` 或 `redis-server`，脚本与文档均已兼容。
3. **H5 子路径**：仓库默认按根路径构建 H5。部署到 `/app/` 时用 `VITE_BASE=/app/ pnpm run build:h5` 构建（`app/vite.config.ts` 已支持，06 号手册 6.2 有说明），否则 `index.html` 引用的 `/assets/...` 会 404；本地开发与预览不加该变量。
4. **云效 UI 与 YAML 参数**：`step`/`component` 的参数名可能随云效版本调整，以云效当前界面与官方文档为准；本 YAML 按官方《YAML 流水线》示例结构编写。
5. **未在本机端到端验证**：ECS 真实部署、云效流水线运行、HTTPS 证书未在本仓库本机验证；文档与脚本按官方文档与仓库实际配置编写，首次部署请按 06 号手册第 10 章逐项验收。
6. **上传目录**：`local` 模式的文件在 `/opt/campus-activity/uploads`，需纳入备份；切 OSS 后由阿里云侧负责。
7. **密钥管理**：`env.example` 只有占位值；真实密码、JWT/签到密钥、OSS/短信凭据只放 `/etc/campus-activity/env`（权限 600），不要提交 Git，也不要打进制品包。
