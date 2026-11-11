# 06 ECS 部署与运维实操手册

> 本手册是校园活动小程序教学包的**部署落地篇**：把本地能跑的项目部署到一台阿里云 ECS（Nginx + systemd + Spring Boot + MySQL + Redis），并接入云效 Flow 流水线。命令级细节只在这里展开，课次文档（01–05）只引用不重复。
>
> 事实口径以 [07-附录-可复现事实清单与验收表](07-附录-可复现事实清单与验收表.md) 为唯一来源；本文与之冲突时以 07 为准。配套脚本在仓库 [`deploy/`](../../deploy/README.md)，逐文件说明见 [deploy/README.md](../../deploy/README.md)。

## 文档信息

| 项 | 内容 |
| --- | --- |
| 文档编号 / 版本 / 适用对象 | CA-TEACH-06 / v1.0（2026-11-11）/ 学生（DevOps、组长）、教师（课前准备服务器） |
| 对应课次 | 第 4 次课「测试、流水线与部署答辩」及课后发布 |
| 云效里程碑 | 流水线 `campus-activity-ci` 跑通、ECS 公网可访问、发布 tag `v1.0.0` |
| 产出物 | 公网 URL、流水线运行记录、部署验收截图 |
| 对应项目文档 | [08-部署与运维手册](../项目文档/08-部署与运维手册.md)、[07-测试报告](../项目文档/07-测试报告.md) |
| 配套资产 | [`deploy/init-server.sh`](../../deploy/init-server.sh)、[`deploy/deploy.sh`](../../deploy/deploy.sh)、[`deploy/campus-activity.service`](../../deploy/campus-activity.service)、[`deploy/nginx/campus-activity.conf`](../../deploy/nginx/campus-activity.conf)、[`deploy/flow/campus-activity-pipeline.yaml`](../../deploy/flow/campus-activity-pipeline.yaml)、[`deploy/env.example`](../../deploy/env.example) |

**阅读方式**：教师课前读第 2～6 章；学生第 4 次课跟做第 5～8 章；出问题查第 9 章；交付前逐项过第 10 章。每节按「做什么 → 命令 → 预期输出 → 失败排查」组织。

> ⚠️ 验证状态：本地命令（构建、脚本语法、YAML 解析、冒烟脚本）已在仓库实测；**云效界面操作与 ECS 真实部署未在本机端到端验证**，界面步骤标注「以云效当前界面为准」，首次执行按第 10 章逐项确认。

---

## 1 部署总览与架构

### 1.1 部署单元

| 部署单元 | 技术栈 | 开发端口 | 生产入口 | 说明 |
| --- | --- | --- | --- | --- |
| 后端应用 | Spring Boot 4.1.1 + MyBatis-Plus 3.5.18.1 + Security 7 + Redis，JDK 21 | 8016 | Nginx 反代 `/api` | 44 个 REST 接口、Swagger、定时任务 |
| 小程序 H5 | uni-app（Vue3）+ Vite | 5174 | Nginx 静态 `/app/` | `app/dist/build/h5` |
| 管理后台 | Vue3 + Vite + Element Plus | 5173 | Nginx 静态 `/` | `admin/dist` |
| MySQL | 8.0+（本机实测 8.4.6） | 3306 | 仅本机/内网 | 库 `campus_activity`，12 张表 |
| Redis | 6.2+（本机实测 8.2.2） | 6379 | 仅本机/内网 | database 11，密码 `abc123`（演示） |
| 本地文件 | 后端 `./uploads` | — | 后端 `/uploads/**` 直出 | 默认本地存储，可切 OSS |

### 1.2 架构（文字版拓扑）

```text
         浏览器 / 微信客户端
                │ HTTP/HTTPS（80/443 公网）
                ▼
      ┌──────────────────────────┐
      │      Nginx（ECS）        │
      │ /          → web/admin   │ 管理后台
      │ /app/      → web/app     │ 小程序 H5
      │ /api/      → 127.0.0.1:8016
      │ /uploads/  → 127.0.0.1:8016
      └──────────┬───────────────┘
                 │ 反向代理（8016 只监听本机）
                 ▼
      ┌──────────────────────────┐
      │ systemd: campus-activity │ java -jar app.jar :8016
      └──────┬──────────┬────────┘
             │ JDBC     │ Redis
             ▼          ▼
      ┌──────────┐  ┌──────────┐
      │MySQL 3306│  │Redis 6379│  只监听 127.0.0.1
      └──────────┘  └──────────┘
```

部署图（项目文档版）见 [项目文档 08 图 8-1](../diagrams/out/D08-部署图.png)。请求链路：浏览器 → Nginx（静态或反代）→ 后端 → MySQL/Redis；上传时 `POST /api/files/upload` 落盘到 `/opt/campus-activity/uploads`，返回的 `/uploads/<objectKey>` 由 Nginx 反代后端直出。**8016、3306、6379 都不对公网开放**。

### 1.3 本地开发 vs 生产环境

| 项目 | 本地开发/演示 | ECS 生产 |
| --- | --- | --- |
| 前端 | Vite 开发服务器（5173/5174） | Nginx 托构建产物 |
| 接口地址 | Vite 代理 `/api`、`/uploads` 到 8016 | 同域 Nginx 反代，无跨域 |
| 后端进程 | `mvn spring-boot:run` 前台运行 | systemd 守护，`Restart=always` |
| 配置来源 | `application.yml` + 本机环境变量 | `/etc/campus-activity/env`（密钥不入仓库） |
| 登录 | `dev-mode=true`，`devOpenId` mock 登录 | `dev-mode=false`，走微信 |
| 短信 | Mock，响应回传 `mockCode` | 建议 `cloopen`，`expose-code=false` |
| 数据库/Redis | 本机 root/演示密码 | 专用账号/强密码，不对公网 |
| 文件存储 | `server/uploads` | `/opt/campus-activity/uploads` 或 OSS |
| 日志 | 终端输出 | journald（`journalctl -u campus-activity`） |
| 部署方式 | 手工起进程 | systemd + `deploy.sh` + 云效流水线 |

### 1.4 部署契约（与 07 号附录第 7、13 节一致）

| 项 | 约定值 |
| --- | --- |
| 安装目录 | `/opt/campus-activity/`（`app.jar`、`web/`、`uploads/`、`logs/`、`releases/`） |
| 运行用户 / 环境文件 | `campus`（非 root）/ `/etc/campus-activity/env` |
| systemd 服务 | `campus-activity.service`，`ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/campus-activity/app.jar` |
| Nginx 站点 | `/etc/nginx/conf.d/campus-activity.conf` |
| 路由 | `/api/`、`/uploads/` → `127.0.0.1:8016`；`/` 后台；`/app/` H5 |
| 后端端口 | 8016（只监听本机，由 Nginx 对外） |
| 部署包 | `/opt/campus-activity/package.tgz`，根目录 `app.jar`、`web/admin/**`、`web/app/**`、`deploy.sh` |
| 健康检查 | `curl -s http://127.0.0.1:8016/api/ping` 返回 `code=0` |

> ✅ 检查点 1：能画出拓扑图，并说明为什么 8016/3306/6379 不能对公网开放。

---

## 2 云资源准备

### 2.1 ECS 规格与镜像

| 项 | 推荐值 | 说明 |
| --- | --- | --- |
| 实例规格 | 2 vCPU / 4 GiB | 后端 + MySQL + Redis + Nginx 同机的最低配置（不是压测结论） |
| 系统盘 | 40 GiB ESSD | 数据、jar、前端、日志都在这 |
| 操作系统 | **Alibaba Cloud Linux 3** 或 **Ubuntu 22.04 LTS** | 本手册脚本双兼容 |
| 带宽 | 按量 1–5 Mbps | 课堂演示够用 |
| 地域 | 离学校近（如华东1/华北2） | 降低延迟 |

> 若还要在这台机器跑前端 Node 构建，建议内存 8 GiB；否则把构建放在云效构建集群或学生本机。

### 2.2 安全组

| 端口 | 来源 | 用途 | 必须 |
| --- | --- | --- | --- |
| 22 | 教师/学生出口 IP（不要 0.0.0.0/0） | SSH、云效主机部署 | 是 |
| 80 | 0.0.0.0/0 | 站点与 `/api` | 是 |
| 443 | 0.0.0.0/0 | HTTPS（可选） | 否 |
| 8016 / 3306 / 6379 | **不开放** | 后端/MySQL/Redis 仅本机 | 禁止 |

> 云效主机部署走 SSH（22），需允许云效构建集群出网 IP；统一由教师配置，学生不要自行放宽。

### 2.3 获取 ECS 的两种模式

| 模式 | 做法 | 适用 |
| --- | --- | --- |
| 学生免费试用 | 阿里云高校计划/免费试用申请 ECS，完成实名与试用开通 | 个人练习 |
| 教师统一申请 | 教师批量开通并统一安全组、主机组、域名，按组分发 IP | 正式课堂（推荐） |

无论哪种，都要记录：**公网 IP、登录方式、地域、到期时间**；分组与机器对应表由教师维护。

### 2.4 域名与备案（可选）

- 直接用 `http://<ECS 公网 IP>` 可完成课程验收；微信小程序真机要求**已备案的 HTTPS 域名**并配置 request 合法域名，课程默认走 H5，不强制。
- 需要 HTTPS 时：域名解析到 ECS → 阿里云「数字证书管理服务」申请免费证书 → 按第 6.6 节启用 443；国内地域域名需完成 ICP 备案。

### 2.5 首次登录与基础检查

```bash
ssh root@<ECS-IP>
cat /etc/os-release | head -n 3          # Alibaba Cloud Linux 3.x / Ubuntu 22.04.x
timedatectl | grep "Time zone"          # 期望 Asia/Shanghai (CST, +0800)
df -h / && free -h                      # / 可用 > 20G，内存 ~4G
curl -sI https://mirrors.aliyun.com | head -n 1   # 出网正常（200/301）
# 时区不对时：sudo timedatectl set-timezone Asia/Shanghai
```

| 失败现象 | 原因 | 处理 |
| --- | --- | --- |
| SSH 连接超时 | 安全组未放行 22 / 实例未启动 | 控制台查实例与安全组 |
| 密码登录被拒 | 镜像禁密码登录或密钥不对 | 控制台 VNC 重置密码/绑定密钥 |
| `curl` 出网失败 | 未分配公网 IP | 绑定 EIP |

> ✅ 检查点 2：`cat /etc/os-release`、`timedatectl`、`df -h` 三条命令结果截图。

---

## 3 服务器初始化

### 3.1 用 `deploy/init-server.sh` 一键初始化（推荐）

**做什么**：安装基础工具、JDK 21、MySQL 8、Redis、Nginx；创建 `campus` 用户与目录；生成 `env`；安装 systemd unit 与 Nginx 站点配置；打印后续手工步骤。脚本幂等，可重复执行。

```bash
# 上传 deploy/ 目录（或在服务器上 git clone 仓库）
scp -r deploy root@<ECS-IP>:/root/deploy
ssh root@<ECS-IP>

# 本地演练（可选）：只打印命令、不改系统、不需要 root
DRY_RUN=1 bash /root/deploy/init-server.sh

# 真正执行
sudo bash /root/deploy/init-server.sh
```

**预期输出**（关键行；`DRY_RUN=1` 时每行前为 `[dry-run]`）：

```text
[init] 操作系统：Alibaba Cloud Linux 3.x（包管理器：dnf）
[init] 已检测到 JDK 21：openjdk version "21.0.x"
[init] MySQL 服务已启动并设为开机自启
[init] Redis 服务已启动并设为开机自启
[init] Nginx 配置校验通过并已 reload
[init] 已生成 /etc/campus-activity/env，请编辑其中的密码与密钥后再启动服务
[init] 已安装 systemd unit：/etc/systemd/system/campus-activity.service
================ 初始化完成 ================
```

| 失败现象 | 原因 | 处理 |
| --- | --- | --- |
| 打印 JDK 手工指引 | 发行版仓库没有 `java-21-openjdk`（常见） | 按 3.5 节装 Temurin/Dragonwell 后重跑脚本 |
| MySQL/Redis 安装失败 | 软件源不可用或版本差异 | 看 WARN 手工安装；或改用云数据库 RDS |
| `nginx -t` 失败 | 端口占用/配置冲突 | 第 6.7 节；Ubuntu 删除默认站点 |
| 重复执行提示用户已存在 | 幂等设计 | 正常 |

### 3.2 脚本做了什么

依次完成：安装基础工具与 JDK21/MySQL8/Redis/Nginx → 建 `campus` 用户与 `/opt/campus-activity/{uploads,logs,releases,web}` → 生成 `/etc/campus-activity/env`（600）→ 安装 systemd unit 与 Nginx 站点配置（`enable` 但不 `start`）→ 打印数据库导入与后续步骤。JDK 按「已有跳过 → 包管理器 → Temurin tar.gz → 手工指引」降级，任何失败都不中断脚本。

### 3.3 手工等价命令（Alibaba Cloud Linux 3 / dnf）

```bash
sudo dnf install -y curl tar rsync git
sudo timedatectl set-timezone Asia/Shanghai

sudo dnf install -y java-21-openjdk-devel || echo "仓库没有 java-21-openjdk，见 3.5"
java -version

sudo dnf install -y mysql-server redis nginx
sudo systemctl enable --now mysqld       # unit 不存在时试 mysql
sudo systemctl enable --now redis        # RHEL 系服务名通常为 redis
sudo systemctl enable --now nginx

sudo useradd --system --create-home --shell /usr/sbin/nologin campus
sudo mkdir -p /opt/campus-activity/{uploads,logs,releases,web} /etc/campus-activity
sudo chown -R campus:campus /opt/campus-activity/uploads /opt/campus-activity/logs

# 在仓库 deploy/ 目录下
sudo cp deploy/env.example /etc/campus-activity/env && sudo chmod 600 /etc/campus-activity/env
sudo cp deploy/campus-activity.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable campus-activity
```

### 3.4 手工等价命令（Ubuntu 22.04 / apt）

```bash
sudo apt-get update
sudo apt-get install -y curl tar rsync git
sudo timedatectl set-timezone Asia/Shanghai

sudo apt-get install -y openjdk-21-jdk-headless || echo "源里没有 openjdk-21，见 3.5"
java -version

sudo apt-get install -y mysql-server redis-server nginx
sudo systemctl enable --now mysql          # Debian/Ubuntu 服务名为 mysql
sudo systemctl enable --now redis-server   # 服务名为 redis-server
sudo systemctl enable --now nginx

sudo useradd --system --create-home --shell /usr/sbin/nologin campus
sudo mkdir -p /opt/campus-activity/{uploads,logs,releases,web} /etc/campus-activity
sudo chown -R campus:campus /opt/campus-activity/uploads /opt/campus-activity/logs

sudo cp deploy/env.example /etc/campus-activity/env && sudo chmod 600 /etc/campus-activity/env
sudo cp deploy/campus-activity.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable campus-activity

# Ubuntu 默认站点会抢占 80：删除后 reload
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

### 3.5 JDK 21 获取：发行版差异与推荐方案

**事实**：JDK 21 在发行版仓库中的可得性不稳定——Alibaba Cloud Linux 3 的 dnf 源不一定有 `java-21-openjdk`，Ubuntu 22.04 官方源也不一定有 `openjdk-21`。不要把「仓库一定装得上」写进部署前提，按 A/B 二选一（推荐 A）。

**A. Temurin 21 tar.gz（推荐，稳定可复现）**

```bash
cd /tmp
curl -fL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
sudo mkdir -p /opt/java21
sudo tar xzf jdk21.tar.gz -C /opt/java21 --strip-components=1

sudo tee /etc/profile.d/java21.sh >/dev/null <<'EOF'
export JAVA_HOME=/opt/java21
export PATH=$JAVA_HOME/bin:$PATH
EOF
sudo ln -sf /opt/java21/bin/java /usr/bin/java
java -version        # 期望 openjdk version "21.0.x"
```

**B. 阿里云 Dragonwell 21 tar.gz**：从 Dragonwell 官网下载 21 x64 Linux `tar.gz`，解压到 `/opt/java21` 后执行与 A 相同的 profile 与软链命令（下载地址随官方版本变化，以官网当前页面为准）。

**C. 发行版仓库（可用则最省事）**：`sudo dnf install -y java-21-openjdk-devel`（ALinux3）或 `sudo apt-get install -y openjdk-21-jdk-headless`（Ubuntu）。发行版差异以实际仓库为准；若仓库只有 JDK 17，**不要凑合**——`pom.xml` 锁定 `java.version=21`，用 17 编译会报 `release version 21 not supported`。

`init-server.sh` 的 JDK 逻辑即「已有 21 跳过 → 试包管理器 → 试 Temurin tar.gz → 打印手工指引」，任何一步失败都不会让脚本退出，装好 JDK 后重跑脚本即可（幂等）。

### 3.6 MySQL 安全初始化

```bash
# Ubuntu 22.04：root 走 auth_socket，先切到 mysql 用户；ALinux3 可直连 mysql -uroot
sudo mysql
```

```sql
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '替换为强密码';
DELETE FROM mysql.user WHERE user='';
DROP DATABASE IF EXISTS test;
FLUSH PRIVILEGES; EXIT;
```

```bash
MYSQL_PWD='替换为强密码' mysql -uroot -e "SELECT VERSION(), @@global.time_zone, NOW();"
# 预期：8.0.x / +08:00（或 SYSTEM）/ 当前时间
```

时区不一致时，在 `/etc/my.cnf`（或 `/etc/mysql/mysql.conf.d/mysqld.cnf`）`[mysqld]` 段加 `default-time-zone='+08:00'` 后重启 MySQL。

### 3.7 Redis 密码与 bind 配置（含服务名差异）

| 发行版 | systemd 服务名 | 配置文件 |
| --- | --- | --- |
| Alibaba Cloud Linux 3 / RHEL 系 | `redis` | `/etc/redis.conf` 或 `/etc/redis/redis.conf` |
| Ubuntu 22.04 / Debian | `redis-server` | `/etc/redis/redis.conf` |

两种服务名兼容写法：

```bash
sudo systemctl status redis-server 2>/dev/null || sudo systemctl status redis
sudo systemctl restart redis-server 2>/dev/null || sudo systemctl restart redis
```

配置密码与只监听本机：

```bash
REDIS_CONF=/etc/redis/redis.conf      # RHEL 系若是 /etc/redis.conf 请改
sudo sed -i -E 's|^[[:space:]]*#?[[:space:]]*requirepass .*|requirepass 替换为强密码|' "$REDIS_CONF"
sudo sed -i -E 's|^[[:space:]]*bind .*|bind 127.0.0.1|' "$REDIS_CONF"
sudo systemctl restart redis 2>/dev/null || sudo systemctl restart redis-server
redis-cli -a '替换为强密码' --no-auth-warning -n 11 ping     # 期望 PONG
```

> 本项目只用 `SET NX EX`、Hash、Lua 等基础能力，**Redis 6.0 即可跑通**；发行版仓库常见 6.0/6.2，建议 6.2+（本机实测 8.2.2）。缓存 TTL 与 key 前缀在 `config/RedisConfig.java`，`application.yml` 不写 `spring.cache.*`。

### 3.8 检查点

```bash
java -version                  # 21.x
mysql --version                # 8.0.x
redis-server --version         # 6.x / 7.x / 8.x
nginx -v                       # nginx/1.x
systemctl is-active mysql 2>/dev/null || systemctl is-active mysqld
systemctl is-active redis 2>/dev/null || systemctl is-active redis-server
systemctl is-active nginx
ls -ld /opt/campus-activity/uploads /opt/campus-activity/logs /etc/campus-activity/env
```

> ✅ 检查点 3：四件套版本正确 + 三个服务 active + 目录与 env 存在。

---

## 4 数据库初始化

### 4.1 准备 SQL 脚本

```bash
# 服务器上已有仓库时
ls server/src/main/resources/db/            # schema.sql  data.sql
# 或从本地传
scp server/src/main/resources/db/{schema.sql,data.sql} root@<ECS-IP>:/root/db/
```

`schema.sql` 开头含 `DROP TABLE IF EXISTS`，是**初始化脚本**；生产禁止重跑，否则清空业务数据（回滚见第 8 章）。

### 4.2 建库并导入

```bash
MYSQL_PWD='root密码' mysql -uroot -e \
  "CREATE DATABASE IF NOT EXISTS campus_activity DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

cd /root/db
MYSQL_PWD='root密码' mysql -uroot campus_activity < schema.sql
MYSQL_PWD='root密码' mysql -uroot campus_activity < data.sql

MYSQL_PWD='root密码' mysql -uroot -N -B -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='campus_activity';"   # 12
MYSQL_PWD='root密码' mysql -uroot campus_activity -e "SELECT COUNT(*) FROM activity;"      # 4（演示数据）
```

> 演示数据以导入当天为基准；开发模式（`dev-mode=true`）下 `DevDataInitializer` 会把过期示例活动平移到未来。生产不导入演示数据。

### 4.3 创建应用专用账号（最小权限）

```bash
MYSQL_PWD='root密码' mysql -uroot <<'SQL'
CREATE USER IF NOT EXISTS 'campus'@'localhost' IDENTIFIED BY '替换为应用专用强密码';
ALTER USER 'campus'@'localhost' IDENTIFIED BY '替换为应用专用强密码';
GRANT SELECT, INSERT, UPDATE, DELETE ON campus_activity.* TO 'campus'@'localhost';
FLUSH PRIVILEGES;
SQL

MYSQL_PWD='应用专用强密码' mysql -ucampus campus_activity -e "SELECT 1;"   # 返回 1
```

把账号密码写入 `/etc/campus-activity/env` 的 `MYSQL_USERNAME` / `MYSQL_PASSWORD`（5.3 节）。

### 4.4 字符集与时区检查

```sql
SHOW CREATE DATABASE campus_activity;          -- 期望 utf8mb4
SELECT @@global.time_zone, @@session.time_zone, NOW();   -- 期望 +08:00 / +08:00
SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.tables
WHERE table_schema='campus_activity' LIMIT 5;  -- 期望 utf8mb4_*
```

### 4.5 常见失败

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Access denied for user 'root'` | 密码错 / Ubuntu auth_socket 未切换 | Ubuntu 用 `sudo mysql` |
| `Unknown collation: utf8mb4_0900_ai_ci` | MySQL < 8.0 | 升级 8.0+，或脚本 collation 改 `utf8mb4_general_ci` |
| 表数量不是 12 | 导入中断/导错库 | 重建库重导；核对库名拼写 |
| 时间整体偏移 8 小时 | 系统/MySQL 时区不对 | 3.6/2.5 节统一时区后重启后端 |
| `data.sql` 报主键冲突 | 脚本被改/未按顺序 | 用仓库原版先 schema 再 data |

> ✅ 检查点 4：12 张表、`activity` 4 条演示数据、专用账号能连库、utf8mb4、时间正确。

---

## 5 后端部署

### 5.1 拿到 jar

```bash
# 方式 A：本地构建（推荐先本地验证）
cd server && mvn -DskipTests package
ls -lh target/campus-activity-server-1.0.0.jar
# 预期：几十 MB fat jar（部署包内重命名为 app.jar）

# 方式 B：云效流水线构建（第 7 章）
# 方式 C：服务器上构建（慢，仅应急）
```

### 5.2 `/opt/campus-activity` 目录结构与权限

```text
/opt/campus-activity/
├── app.jar            # 后端可执行 jar（root:root 644）
├── web/admin/         # 管理后台产物（admin/dist）
├── web/app/           # 小程序 H5 产物（app/dist/build/h5）
├── uploads/           # 本地上传目录（campus:campus 755，需备份）
├── logs/              # 备用日志目录（campus:campus）
├── releases/          # 历史 jar 快照（deploy.sh 维护，回滚用）
└── deploy.sh          # 部署脚本（root:root 755）
```

```bash
sudo mkdir -p /opt/campus-activity
sudo chown -R campus:campus /opt/campus-activity/uploads /opt/campus-activity/logs
```

### 5.3 环境变量文件 `/etc/campus-activity/env`

生产密钥的唯一入口：systemd 读取后注入进程，不写进 `application.yml`、不进 Git。模板 [`deploy/env.example`](../../deploy/env.example)。

**映射原理**：Spring Boot 把环境变量按「大写 + 下划线」映射到配置项（`CAMPUS_JWT_SECRET → campus.jwt.secret`、`CAMPUS_DEV_MODE → campus.dev-mode`）；`application.yml` 已声明 `${MYSQL_*}`、`${REDIS_*}`、`${WX_*}`、`${OSS_*}`、`${CLOOPEN_*}` 占位符。

```bash
sudo cp deploy/env.example /etc/campus-activity/env
sudo chmod 600 /etc/campus-activity/env
sudo vim /etc/campus-activity/env
```

| 变量 | 对应配置 | 说明 / 生产要求 |
| --- | --- | --- |
| `JAVA_OPTS` | JVM 参数 | 2C4G 用 `-Xms256m -Xmx512m`；含 `-Duser.timezone=Asia/Shanghai` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | `spring.datasource.*` | **必改**：第 4.3 节专用账号，不用 root |
| `REDIS_HOST` / `REDIS_PORT` | `spring.data.redis.host/port` | `127.0.0.1` / `6379` |
| `REDIS_PASSWORD` | `spring.data.redis.password` | **必改**：第 3.7 节强密码 |
| `REDIS_DATABASE` | `spring.data.redis.database` | `11`（与 01–14 隔离） |
| `WX_APPID` / `WX_SECRET` | `campus.wechat.*` | 非 dev 模式必填 |
| `CAMPUS_DEV_MODE` | `campus.dev-mode` | **必改 false**，否则任何人可 mock 登录 |
| `CAMPUS_JWT_SECRET` | `campus.jwt.secret` | **必改**：≥32 字符随机串（`openssl rand -base64 48`） |
| `CAMPUS_CHECKIN_SECRET` | `campus.checkin.secret` | **必改**：独立随机串，多实例一致 |
| `CAMPUS_SMS_PROVIDER` | `campus.sms.provider` | `mock` / `cloopen` |
| `CAMPUS_SMS_EXPOSE_CODE` | `campus.sms.expose-code` | **必改 false**，否则验证码随响应泄露 |
| `CAMPUS_FILE_STORAGE_TYPE` | `campus.file.storage-type` | `local` / `oss` |
| `LOGGING_LEVEL_COM_CAMPUS_ACTIVITY` | `logging.level.com.campus.activity` | 生产 `info`，排障临时 `debug` |
| `CLOOPEN_*` / `OSS_*` | `campus.sms.cloopen.*` / `campus.file.oss.*` | 对应通道/存储启用时必填 |

> `env` 里除 `JAVA_OPTS` 外不要给值加引号；改完必须 `sudo systemctl restart campus-activity` 才生效。JWT 密钥少于 32 字符会启动失败，这是有意的保护。

### 5.4 安装 systemd 服务

```bash
sudo cp deploy/campus-activity.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable campus-activity
sudo systemctl start campus-activity
sudo systemctl status campus-activity --no-pager     # 期望 Active: active (running)
```

| unit 配置 | 值 | 作用 |
| --- | --- | --- |
| `User` / `Group` | `campus` | 非 root 运行 |
| `WorkingDirectory` | `/opt/campus-activity` | 决定 `./uploads` 落点 |
| `EnvironmentFile` | `/etc/campus-activity/env` | 注入密钥；缺失会启动失败（有意保护） |
| `ExecStart` | `/usr/bin/java $JAVA_OPTS -jar /opt/campus-activity/app.jar` | 启动 |
| `Restart` | `always` | 崩溃自动拉起 |
| `StandardOutput/Error` | `journal` | 日志进 journald |

### 5.5 启停与状态

```bash
sudo systemctl start|stop|restart campus-activity
sudo systemctl status campus-activity --no-pager
sudo systemctl is-active campus-activity     # active
sudo systemctl is-enabled campus-activity    # enabled
```

> 课程统一用 `deploy.sh` 发布（第 8 章），它内部就是 `systemctl restart` + 健康检查 + Nginx reload，并额外负责备份与静态资源同步。

### 5.6 健康检查

```bash
curl -s http://127.0.0.1:8016/api/ping
# 期望：{"code":0,"message":"ok","data":{"service":"campus-activity","status":"up"}}
curl -s -o /dev/null -w '%{http_code}\n' -L http://127.0.0.1:8016/swagger-ui.html    # 200
MYSQL_PWD='应用专用强密码' mysql -ucampus campus_activity -e "SELECT 1;"              # 1
redis-cli -a 'Redis强密码' --no-auth-warning -n 11 ping                                # PONG
ss -lntp | grep 8016        # 期望 127.0.0.1:8016；若 0.0.0.0:8016 需查暴露面
```

健康检查接口真实返回见 [截图 api-ping](../screenshots/runtime/api-ping.png)。

### 5.7 日志（journalctl）

```bash
sudo journalctl -u campus-activity -f                      # 实时
sudo journalctl -u campus-activity -n 100 --no-pager       # 最近 100 行
sudo journalctl -u campus-activity -b --no-pager           # 本次启动以来
sudo journalctl -u campus-activity -p err -n 50 --no-pager # 只看 ERROR
```

启动成功标志：`Started CampusActivityApplication in x seconds`，以及 `本地文件访问映射：/uploads/** -> file:/opt/campus-activity/uploads/`。

### 5.8 常见失败

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `failed (Result: exit-code)` | env 缺失/配置错/JWT 太短/数据库连不上 | `journalctl -u campus-activity -n 100` 看第一段异常 |
| `Port 8016 was already in use` | 旧进程未退出 | `ss -lntp \| grep 8016` 找 PID，确认后 `kill -15` |
| `campus.jwt.secret 长度至少 32 个字符` | 密钥太短 | 换 ≥32 字符随机串后 restart |
| `Communications link failure` / `Access denied` | MySQL 未启动/账号密码错 | 5.6 节用同一账号手工连库，核对 env |
| `Unable to connect to Redis` | Redis 未启动/密码或 database 错 | 5.6 节 4)；核对 `REDIS_*` |
| `storage-type 设为 oss，但 ... 没有配置` | 切 OSS 但 `OSS_*` 未配齐 | 补齐 env 或改回 `local` |
| 找不到日志文件 | systemd 写 journald 而非文件 | 用 `journalctl` 而不是 `ls logs/` |

> ✅ 检查点 5：`/api/ping` 返回 0、服务 running、journal 无 ERROR、8016 只监听本机。

---

## 6 前端部署与 Nginx

### 6.1 构建两个前端

在**本地或云效构建集群**构建（不建议在 ECS 装 Node，占内存）：

```bash
cd admin && pnpm install && pnpm run build
ls dist                       # assets/  index.html  → web/admin
cd app && pnpm install && VITE_BASE=/app/ pnpm run build:h5
ls dist/build/h5              # assets/  index.html  static/  → web/app
```

产物目录口径：`admin/dist/`、`app/dist/build/h5/`（与 07 号附录第 2 节一致）。

### 6.2 H5 部署在 `/app/` 的关键配置（重点）

仓库默认按**根路径**构建 H5，`index.html` 引用的是 `/assets/...` 绝对路径。直接放到 `/app/` 下访问会去站点根找资源、命中管理后台资源，导致 H5 **白屏/404**。

**部署到 `/app/` 子路径前必须让产物按 `/app/` 生成路径**。推荐用构建环境变量，代码零改动（`app/vite.config.ts` 已支持 `VITE_BASE`，默认 `/`，本地开发不受影响）：

```bash
cd app
VITE_BASE=/app/ pnpm run build:h5
grep -o '/app/assets/[^"]*' dist/build/h5/index.html | head -n 2   # 能匹配说明 base 生效
```

预期输出示例（哈希名可能不同）：

```text
/app/assets/index-CCctRH-3.js
/app/assets/index-BEypNI07.css
```

如果团队更偏好静态配置，也可以在 `app/src/manifest.json` 增加下面的 `h5` 段并提交到代码库（二选一，不要与 `VITE_BASE` 同时改）：

```jsonc
// app/src/manifest.json（JSONC，uni-app 能解析）
{
  "name": "校园活动小程序",
  // ... 其他原有配置 ...
  "h5": { "title": "校园活动", "router": { "base": "/app/" } },
  "vueVersion": "3"
}
```

> 不同 uni-app 版本对子路径配置的键名可能不同；若 `h5.router.base` 不生效，优先使用上面的 `VITE_BASE=/app/` 环境变量方案（本手册默认方案，已在本地构建验证）。
>
> **替代方案**：H5 与后台各用一个子域名，均部署在各自站点根路径（`admin.example.com` → `web/admin`，`h5.example.com` → `web/app`），Nginx 复制两个 `server` 块即可。本手册默认采用 07 号附录约定的单站点 `/` + `/app/` 方案。

### 6.3 上传静态产物

```bash
scp -r admin/dist/* root@<ECS-IP>:/opt/campus-activity/web/admin/
scp -r app/dist/build/h5/* root@<ECS-IP>:/opt/campus-activity/web/app/
# 云效流水线部署时由 deploy.sh 自动同步 web/（第 7、8 章），无需手工 scp
```

### 6.4 安装 Nginx 站点配置

```bash
sudo cp deploy/nginx/campus-activity.conf /etc/nginx/conf.d/campus-activity.conf
sudo vim /etc/nginx/conf.d/campus-activity.conf     # 改 server_name 为真实域名或 _
sudo nginx -t
# 预期：nginx: configuration file /etc/nginx/nginx.conf test is successful
sudo nginx -s reload        # 或 sudo systemctl reload nginx
```

| 配置 | 作用 / 注意 |
| --- | --- |
| `upstream campus_activity_backend { server 127.0.0.1:8016; keepalive 16; }` | 后端上游只指本机 |
| `client_max_body_size 10m` | **必须与后端 `spring.servlet.multipart.max-file-size=10MB` 对齐**，否则上传在 Nginx 层 413 |
| `location /api/ { proxy_pass http://campus_activity_backend; }` | `proxy_pass` **不带 URI**（结尾无 `/`），保留 `/api` 前缀；写成 `...8016/` 会丢 `/api` 导致 404 |
| `proxy_set_header Host/X-Real-IP/X-Forwarded-For/X-Forwarded-Proto` | 透传客户端信息 |
| `location /uploads/ { proxy_pass ...; }` | 同样保留 `/uploads` 前缀；本地模式下由后端直出 |
| `location ^~ /app/ { alias .../web/app/; try_files $uri $uri/ /app/index.html; }` | H5 静态，依赖 6.2 的 base 配置 |
| `location ^~ /assets/ { expires 30d; add_header Cache-Control ...immutable; }` | 带 hash 的资源长缓存 |
| `location = /index.html { add_header Cache-Control "no-store"; }` | 入口不缓存，避免发版后拿旧页面 |
| `gzip on; gzip_types ...` | 压缩 JS/CSS/JSON |
| `location / { try_files $uri $uri/ /index.html; }` | 后台 SPA 回落（hash 路由兜底） |

### 6.5 验证静态站点

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/          # 200 管理后台
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/app/      # 200 H5（404 查 6.2）
curl -s http://127.0.0.1/api/ping                                  # code=0（经 Nginx 反代）
curl -s http://<ECS-IP>/api/ping                                   # 公网验证
```

浏览器：后台 `http://<ECS-IP>/`（演示账号 `admin / Admin@123`，生产先改密码）；H5 `http://<ECS-IP>/app/`（`dev-mode=true` 时可用 `mock-student-1`）。

### 6.6 HTTPS（可选）

- **阿里云证书（推荐）**：数字证书管理服务申请免费证书 → 下载 Nginx 格式 → 上传 `.pem`/`.key` → 启用配置末尾注释的 443 server 块 → `sudo nginx -t && sudo nginx -s reload` → 80 改 301 跳转。
- **certbot**：`sudo apt-get install -y certbot python3-certbot-nginx && sudo certbot --nginx -d campus.example.com`，再用 `sudo certbot renew --dry-run` 验证续期。

### 6.7 常见失败

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 访问 `/` 是 Nginx 欢迎页 | 默认站点抢占 | `sudo rm -f /etc/nginx/sites-enabled/default` 后 reload |
| `/api/ping` 404 | `proxy_pass` 多写 `/` 丢掉 `/api`；后端未起 | 对照 6.4；`curl 127.0.0.1:8016/api/ping` |
| `/api/ping` 502 | 8016 未监听/后端崩溃 | `systemctl status`、`journalctl` |
| `/app/` 白屏、`/assets` 404 | H5 未按 `/app/` base 构建 | 6.2 重新构建 |
| 上传 413 | `client_max_body_size` 太小 | 配置为 `10m`，与后端对齐 |
| `/uploads/...` 404 | 目录不对/base-dir 被改/OSS 模式仍访问 `/uploads` | 看 journal 映射路径；确认 `CAMPUS_FILE_STORAGE_TYPE` |
| `nginx -t` duplicate server name | 两个 conf 同域名 | 只保留一个站点配置 |
| 改配置不生效 | 忘记 reload | `sudo nginx -t && sudo nginx -s reload` |

> ✅ 检查点 6：`nginx -t` 成功；公网 `/` 打开后台登录页；`/app/` 打开 H5；`/api/ping` 返回 `code=0`。

---

## 7 云效流水线部署

> 本章界面名称与按钮位置随云效版本变化，**一律以云效当前界面为准**；官方参考见附录 B。学生版逐步操作见 [05-云效平台操作手册（学生版）](05-云效平台操作手册（学生版）.md) 第 5 章。

### 7.1 前置准备（教师/DevOps）

| 前置项 | 要求 |
| --- | --- |
| 云效项目与代码库 | `CA-<组号>-校园活动` / `campus-activity-<组号>`，主分支 `main`（07 号附录第 6 节） |
| Codeup 服务连接 | 流水线拉代码的认证（云效「设置 → 服务连接」创建并共享） |
| ECS 初始化 | 已执行第 3～6 章，`/etc/campus-activity/env` 已配置，Nginx 可用 |
| 主机组 | ECS 已接入云效主机组，状态「在线」 |
| JDK 21 | 主机已安装（流水线只下载制品并重启服务，不装 JDK） |

### 7.2 创建主机组并接入 ECS

1. 云效「流水线 / Flow」→「主机组」→「新建主机组」（以当前界面为准）。
2. 添加主机：ECS 公网 IP、SSH 端口 `22`、用户名 `root`。
3. 认证选 **SSH 密钥**：教师生成密钥对，公钥追加到 ECS `~/.ssh/authorized_keys`，私钥填入云效凭据。
4. 保存后等待状态「在线」；离线先查安全组 22 与密钥。
5. 复制**主机组 ID**（详情页），填 YAML 的 `<MACHINE_GROUP_ID>`。

### 7.3 从 Java 模板创建流水线

1. 「流水线」→「新建流水线」→ 模板搜索 **Java** → 「Java 应用构建并部署到 ECS 主机」→「使用模板」。
2. 流水线名称改为 `campus-activity-ci`（按此名验收）。
3. 配置代码源：Codeup 服务连接 + 代码库 `campus-activity-<组号>`，分支 `main`。
4. 按 7.4–7.6 配置构建/制品/部署，保存后先手动运行一次。

### 7.4 构建阶段（JDK 21 + Node 20）

**关键事实**：后端在 `server/` 子目录，**构建目录必须设为 `server`**，否则找不到 `pom.xml`。

| 步骤 | 配置 | 命令 / 产物 |
| --- | --- | --- |
| 后端打包 | JDK 21、Maven 3.9+，工作目录 `server` | `mvn -B clean package -DskipTests` → `server/target/campus-activity-server-1.0.0.jar` |
| 管理后台 | Node 20，工作目录仓库根 | `cd admin && pnpm install && pnpm run build` → `admin/dist` |
| 小程序 H5 | Node 20 | `cd app && pnpm install && VITE_BASE=/app/ pnpm run build:h5` → `app/dist/build/h5` |
| 整理制品结构 | 执行命令步骤 | 产出 `app.jar`、`web/admin`、`web/app`、`deploy.sh` |

**为什么默认 `-DskipTests`**：本仓库 20 个测试是 `@SpringBootTest`，依赖真实 MySQL（`campus_activity`）与 Redis（`abc123`/db11），云效公共构建集群默认没有这两个服务，`mvn test` 会失败。课程标准做法：流水线跳过测试打包；测试证据用本地 `mvn -o test`（20/20，07 号附录第 11 节）+ Testhub 记录；自建构建集群装好依赖后再打开 YAML 的 `test_stage`。

整理制品结构命令（与 YAML `stage_artifact` 一致）：

```bash
set -e
rm -rf web app.jar deploy.sh && mkdir -p web
cp server/target/campus-activity-server-1.0.0.jar app.jar
cp -r admin/dist web/admin
cp -r app/dist/build/h5 web/app
cp deploy/deploy.sh deploy.sh
```

### 7.5 制品上传（`package.tgz` 结构）

与 07 号附录第 13 节一致，**压缩包根目录必须恰好是这 4 个条目**：

```text
package.tgz
├── app.jar            # = campus-activity-server-1.0.0.jar
├── web/admin/**       # = admin/dist
├── web/app/**         # = app/dist/build/h5
└── deploy.sh          # 支持 start|stop|restart|status
```

云效「构建物上传」选「归档至云效公共存储空间」，制品名 `default`，打包路径填 `app.jar`、`web`、`deploy.sh`（相对工作空间根目录，见 YAML `upload_step`）。若界面有「制品中包含打包路径的目录」选项，确认压缩包内不出现多余 `package/` 前缀（以当前界面为准）。

### 7.6 主机部署

| 配置项 | 值 |
| --- | --- |
| 制品 | 上游「构建物上传」产物（`$[stages.build_stage.build_job.upload_step.artifacts.default]`） |
| 主机组 | 7.2 创建的主机组（`<MACHINE_GROUP_ID>`） |
| 下载路径 | `/opt/campus-activity/package.tgz` |
| 执行用户 | `root` |
| 部署脚本 | 见下 |

```bash
mkdir -p /opt/campus-activity
tar zxvf /opt/campus-activity/package.tgz -C /opt/campus-activity/
sh /opt/campus-activity/deploy.sh restart
```

`deploy.sh restart` 会依次：备份/归档 jar → 同步 `web/` → `systemctl restart campus-activity` → 轮询 `/api/ping` → `nginx -t && nginx -s reload`；任一环节失败以非 0 退出（流水线变红）。

> 直接解压会覆盖旧 `app.jar`，`deploy.sh` 每次归档当前 jar 到 `releases/`，回滚用上一次发布的快照。若要「发布前精确保留正在运行的旧 jar」，用预发目录方式（YAML 注释已给出）：`mkdir -p /opt/campus-activity/staging && tar zxvf .../package.tgz -C /opt/campus-activity/staging/ && sh /opt/campus-activity/staging/deploy.sh restart`。

### 7.7 YAML 流水线导入与 4 个占位符

仓库提供 [`deploy/flow/campus-activity-pipeline.yaml`](../../deploy/flow/campus-activity-pipeline.yaml)，结构与界面配置一一对应：

| stage | job | step | 作用 |
| --- | --- | --- | --- |
| `test_stage`（可选） | `backend_test_job` | `JavaBuild`：`cd server && mvn -B test` | 20 个用例；公共集群无 MySQL/Redis，课堂跳过 |
| `build_stage` | `build_job`（同一 job 共享工作空间） | `JavaBuild`、`NodeBuild`×2、`Command`、`ArtifactUpload` | 产出 `app.jar`/`web`/`deploy.sh` |
| `deploy_stage` | `deploy_job` | `VMDeploy` | 下载 `package.tgz` → `deploy.sh restart` |

**需要替换的 4 个占位符**（与 05 号手册第 2.4 节一致）：

| 占位符 | 含义 | 从哪里拿 |
| --- | --- | --- |
| `<REPO_NAME>` | 代码库展示名 | Codeup 代码库列表，如 `campus-activity-01` |
| `<CODEUP_REPO_URL>` | 代码库 clone 地址（HTTPS） | 代码库右上「克隆/下载」 |
| `<SERVICE_CONNECTION_ID>` | 服务连接 ID（代码源认证） | 云效「设置 → 服务连接」，教师创建并共享 |
| `<MACHINE_GROUP_ID>` | ECS 主机组 ID | Flow → 主机组 → 详情 |

导入方式（以当前界面为准）：Flow → 新建流水线 → 「YAML 流水线 / 导入 YAML」→ 粘贴或指定仓库路径 → 替换 4 个占位符 → 保存后手动运行一次。

> 两个必须知道的坑：① 构建与制品上传必须在**同一个 job**（不同 job 工作空间隔离，产物互相找不到）；② H5 部署在 `/app/` 时先按 6.2 配好 base 再构建，否则白屏。

### 7.8 运行、失败重跑与日志定位

- **看日志**：运行页 → 各任务卡片「日志」；构建失败看 Maven/Node 输出，部署失败看「部署详情 → 主机日志」。**重跑**：运行历史 →「重新运行」，或修复后重新触发；只重跑失败阶段时注意制品是否已上传。

| 现象 | 常见原因 | 定位 |
| --- | --- | --- |
| 拉代码失败 | 服务连接/分支/权限 | 第一步日志 |
| `pom.xml` 找不到 | 构建目录没设 `server` | Maven 日志 |
| 依赖下载失败 | 构建集群网络受限 | 走 7.9 降级 |
| 测试阶段失败 | 公共集群无 MySQL/Redis | 跳过测试阶段 |
| 主机部署失败 | 主机离线/权限/制品路径 | 主机组状态 → 手工 SSH 验证 |
| 部署后 502 | 后端没起/env 未配置 | ECS 上 `journalctl -u campus-activity` |

### 7.9 降级方案（云端构建受限时）

1. 本地构建后端与两个前端（5.1、6.1 节）；
2. 本地按 7.5 结构打成 `package.tgz`；
3. `scp` 上传到 ECS，手工 `tar zxvf ... && sh deploy.sh restart`；
4. 云效侧保留「主机部署」步骤或手工部署记录，成果以公网 URL + 部署截图为准。

> ✅ 检查点 7：流水线运行成功；ECS 下出现新 `app.jar`、`web/`、`releases/` 新快照；公网 `/api/ping` 返回 `code=0`；静态站点可打开。

---

## 8 发布、回滚与备份

### 8.1 版本号与 tag

```bash
git checkout main && git pull
git tag -a v1.0.0 -m "校园活动小程序 v1.0.0：核心链路 + 扩展能力 + 部署"
git push origin v1.0.0
git tag -l            # 预期：v1.0.0
```

在云效「运行记录 / 发布记录」中把 tag `v1.0.0` 与流水线运行关联（以当前界面为准）。

### 8.2 `deploy.sh` 的备份与发布机制

```bash
sh /opt/campus-activity/deploy.sh restart|start|stop|status
```

| 动作 | 说明 |
| --- | --- |
| 备份/归档 | 预发目录方式先备份正在运行的 `app.jar` 为 `releases/app.jar.rollback.<时间戳>`；每次发布归档当前 jar 为 `releases/app.jar.<时间戳>`，默认保留最近 5 个 |
| 同步静态资源 | 制品含 `web/` 时同步到 `/opt/campus-activity/web`（有 `rsync` 用 `--delete` 清旧 hash 文件） |
| 重启服务 | `systemctl restart campus-activity` |
| 健康检查 | 轮询 `/api/ping`（默认 60 秒），失败打印 journal 最近 80 行并非 0 退出 |
| Nginx | 站点配置存在时 `nginx -t && nginx -s reload`，校验失败非 0 退出 |

```bash
ls -lt /opt/campus-activity/releases | head    # 查看历史版本
```

### 8.3 数据库备份

```bash
mkdir -p /root/campus-backup
MYSQL_PWD='应用专用强密码' mysqldump -ucampus -h127.0.0.1 -P3306 \
  --single-transaction --default-character-set=utf8mb4 --routines --triggers \
  campus_activity > /root/campus-backup/campus_activity_$(date +%Y%m%d_%H%M%S).sql
ls -lh /root/campus-backup/ && grep -c "CREATE TABLE" /root/campus-backup/campus_activity_*.sql
# 预期：12
```

> 用 `MYSQL_PWD` 而非 `-p密码`，避免密码进入 shell 历史与进程列表；备份目录权限设 700。

### 8.4 上传目录备份（本地存储模式）

```bash
tar czf /root/campus-backup/uploads_$(date +%Y%m%d_%H%M%S).tgz -C /opt/campus-activity uploads
```

切 OSS 后文件在阿里云侧，按 OSS 版本/备份策略管理，不再备份本地目录。

### 8.5 回滚步骤

**应用回滚**：

```bash
ls -lt /opt/campus-activity/releases | head          # 选目标版本
sudo systemctl stop campus-activity
sudo cp -f /opt/campus-activity/releases/app.jar.<时间戳> /opt/campus-activity/app.jar
sudo chown root:root /opt/campus-activity/app.jar
sudo systemctl start campus-activity
curl -s http://127.0.0.1:8016/api/ping                # code=0
```

**前端回滚**：重新上传上一版 `admin/dist`、`app/dist/build/h5` 到 `web/`，`sudo nginx -s reload`。前端回滚不影响后端与数据库，优先用于「只是页面改坏」。

**数据库回滚**（高风险）：

```bash
sudo systemctl stop campus-activity
# 先对当前库做保护性备份，再恢复发布前备份
MYSQL_PWD='应用专用强密码' mysqldump -ucampus --single-transaction campus_activity \
  > /root/campus-backup/before_restore_$(date +%Y%m%d_%H%M%S).sql
MYSQL_PWD='应用专用强密码' mysql -ucampus campus_activity \
  < /root/campus-backup/campus_activity_20261110_020000.sql
```

> `schema.sql` 含 `DROP TABLE`，**生产禁止直接重跑**；整库恢复会丢失备份点之后的业务数据，能用定点 SQL 修复就不要整库恢复。

### 8.6 回滚验证与记录

回滚后依次验证：`/api/ping` 返回 0 → journal 无启动异常 → 后台能登录、H5 能打开 → 关键数据量与预期一致 → 通知相关方。并留档：回滚时间/触发原因/范围与版本/数据影响/执行人与确认人/验证结果/后续动作，作为复盘依据。

---

## 9 运维与排错

### 9.1 日常健康检查

| 检查项 | 命令 | 期望 |
| --- | --- | --- |
| 服务状态 | `systemctl is-active campus-activity` | `active` |
| 探活 | `curl -s http://127.0.0.1:8016/api/ping` | `code=0` |
| 经 Nginx | `curl -s http://<ECS-IP>/api/ping` | `code=0` |
| 端口 | `ss -lntp \| grep -E '8016\|3306\|6379'` | 仅本机/内网 |
| 数据库 | `MYSQL_PWD=... mysql -ucampus campus_activity -e "SELECT COUNT(*) FROM activity;"` | 正常返回 |
| Redis | `redis-cli -a '密码' --no-auth-warning -n 11 ping` | `PONG` |
| 磁盘 | `df -h` | 剩余 > 15% |
| 日志错误 | `journalctl -u campus-activity -p err -n 50 --no-pager` | 无持续错误 |
| 上传目录 | `ls -ld /opt/campus-activity/uploads` | 存在且 campus 可写 |
| 备份 | `ls -lt /root/campus-backup \| head` | 有最近备份 |

### 9.2 常见故障排查表

| 现象 | 可能原因 | 排查与处理 |
| --- | --- | --- |
| 浏览器打不开站点 | 安全组 80 未放行 / Nginx 未启动 | 控制台安全组；`systemctl status nginx` |
| 打开是 Nginx 欢迎页 | 默认站点抢占 | 删 `/etc/nginx/sites-enabled/default` 后 reload |
| 502 Bad Gateway | 后端没起 / 8016 未监听 | `systemctl status`、`journalctl`、`curl 127.0.0.1:8016/api/ping` |
| `/api` 404 | `proxy_pass` 带 URI 丢 `/api` | 对照 6.4 节；`nginx -t` |
| 上传 413 | `client_max_body_size` 太小 | 保持 `10m` 与后端对齐 |
| 上传 70003 | 扩展名不在白名单 / 图片内容与扩展名不符 | 用真实 PNG/JPG；白名单 `campus.file.allowed-extensions` |
| 上传成功但图片 404 | `/uploads` 反代缺失 / 切了 OSS / base-dir 不对 | 看 journal 映射；`CAMPUS_FILE_STORAGE_TYPE` |
| 401（code 10001） | 未带/过期 token、换了 JWT 密钥 | 重新登录；换密钥后旧 token 全失效属正常 |
| 403（code 10002） | 角色不匹配（学生访问后台等） | 用对应角色账号登录 |
| 接口 500 且日志 Redis 异常 | Redis 密码/database 错或未启动 | 5.6 节 4)；缓存可 flushdb 重建（会清短信/黑名单，谨慎） |
| 数据库连接池耗尽 | 连接未释放 / MySQL 挂 / 账号错 | `SHOW PROCESSLIST`；`systemctl status mysql`；核对 env |
| 活动时间偏移 8 小时 | 系统/MySQL/JVM 时区不一致 | `timedatectl`、`SELECT NOW()`、`JAVA_OPTS` 时区参数 |
| 部署脚本非 0 退出 | 健康检查失败 / `nginx -t` 失败 | 按脚本打印的 journal 尾部排查后重跑 |
| 磁盘满 | journal/备份/上传目录增长 | `journalctl --vacuum-time=7d`；清理旧备份；上传目录归档 |
| 云效主机离线 | 安全组 22 / 密钥 / 实例关机 | 教师处理；学生不要反复重试 |

### 9.3 日志轮转

systemd 服务默认写 journald：

```bash
journalctl --disk-usage                  # 查看占用
sudo journalctl --vacuum-time=14d        # 只保留 14 天
sudo journalctl --vacuum-size=500M       # 或限制最大 500M
# 持久化：/etc/systemd/journald.conf 设 SystemMaxUse=500M、MaxRetentionSec=14day，改后重启 systemd-journald
```

若改回 `nohup` 写 `logs/campus-activity.log`，用 logrotate（`copytruncate` 适合进程持有句柄）：

```ini
# /etc/logrotate.d/campus-activity
/opt/campus-activity/logs/campus-activity.log {
    daily
    rotate 14
    missingok
    notifempty
    compress
    copytruncate
}
```

### 9.4 安全加固清单

| 项 | 要求 | 检查 |
| --- | --- | --- |
| 演示开关 | `CAMPUS_DEV_MODE=false` | `grep CAMPUS_DEV_MODE /etc/campus-activity/env` |
| 验证码回传 | `CAMPUS_SMS_EXPOSE_CODE=false` | 同上 |
| 密钥 | JWT/签到密钥 ≥32 字符且随机；数据库/Redis 强密码 | 逐项核对 env；`openssl rand -base64 48` |
| 端口 | 8016/3306/6379 不对公网；安全组只放 22/80/443 | `ss -lntp` + 安全组 |
| 数据库账号 | 应用最小权限账号，不用 root | `SHOW GRANTS FOR 'campus'@'localhost'` |
| Redis | `requirepass` + `bind 127.0.0.1` | `redis-cli CONFIG GET bind` |
| 密钥文件 | env 权限 600 root:root，不入 Git、不进制品包 | `ls -l /etc/campus-activity/env` |
| Swagger | 生产建议限制访问来源或关闭 | 是否允许公网访问 `/swagger-ui.html` |
| 系统 | 定期 `dnf update`/`apt upgrade` | 按运维规范 |

---

## 10 验收清单

### 10.1 部署验收（逐项勾选）

| # | 类别 | 检查项 | 命令/方法 | 期望 | ☐ |
| --- | --- | --- | --- | --- | --- |
| 1 | 服务器 | JDK21/MySQL8/Redis/Nginx 已装 | 四件套版本命令 | 符合第 3 章 | ☐ |
| 2 | 服务器 | 时区 Asia/Shanghai | `timedatectl` | 正确 | ☐ |
| 3 | 数据库 | 12 张表、utf8mb4 | 4.4 节 SQL | 12 / utf8mb4 | ☐ |
| 4 | 数据库 | 专用账号可连库 | `mysql -ucampus ... -e "SELECT 1"` | 返回 1 | ☐ |
| 5 | Redis | database 11 可读写 | `redis-cli ... -n 11 ping` | PONG | ☐ |
| 6 | 后端 | 服务 active 且无异常 | `systemctl status` / `journalctl` | running / 无 ERROR | ☐ |
| 7 | 后端 | 探活 | `curl 127.0.0.1:8016/api/ping` | `code=0` | ☐ |
| 8 | 后端 | dev-mode 已关闭 | env 中 `CAMPUS_DEV_MODE=false`，mock 登录失败 | 登录失败 | ☐ |
| 9 | 后端 | 上传链路 | 管理员 token 上传真实 PNG | `code=0`，url 可访问 | ☐ |
| 10 | 网关 | `nginx -t` 通过 | `sudo nginx -t` | successful | ☐ |
| 11 | 网关 | 后台公网可访问 | `http://<IP>/` | 登录页 | ☐ |
| 12 | 网关 | H5 公网可访问 | `http://<IP>/app/` | H5 首页 | ☐ |
| 13 | 网关 | API 经 Nginx | `http://<IP>/api/ping` | `code=0` | ☐ |
| 14 | 网关 | 上传文件可访问 | 打开返回的 `/uploads/...` | 200 + 图片 | ☐ |
| 15 | 流水线 | `campus-activity-ci` 成功 | 云效运行记录 | 全部阶段绿 | ☐ |
| 16 | 发布 | tag `v1.0.0` 已推送并关联运行 | `git tag -l` + 云效 | v1.0.0 | ☐ |
| 17 | 安全 | 8016/3306/6379 不对公网 | 端口探测/安全组 | 不可达 | ☐ |
| 18 | 备份/回滚 | 完成一次备份 + 应用回滚演练 | 8.3/8.5 节 | 文件存在、回滚后探活正常 | ☐ |

### 10.2 冒烟脚本与 dev-mode 的关系（重要）

`./scripts/smoke.sh` 的 33 项断言包含 mock 登录、短信验证码登录等**开发模式链路**，要求在**演示环境**（`campus.dev-mode=true`）执行：

```bash
cd campus-activity && ./scripts/smoke.sh
# 预期：全部通过：33 项，退出码 0
```

**生产 `dev-mode=false` 时 mock 登录不可用**，冒烟脚本无法通过学生登录步骤。生产验收改用：

```bash
# 1) 探活
curl -s http://<ECS-IP>/api/ping

# 2) 管理员登录（生产先改演示密码）
TOKEN=$(curl -s -X POST http://<ECS-IP>/api/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# 3) 带 token 访问后台接口
curl -s http://<ECS-IP>/api/admin/dashboard -H "Authorization: Bearer $TOKEN" | head -c 200

# 4) 上传真实 PNG（扩展名与文件头都要是 PNG）
curl -s -X POST "http://<ECS-IP>/api/files/upload?bizType=activity-cover" \
  -H "Authorization: Bearer $TOKEN" -F 'file=@cover.png;type=image/png'

# 5) 浏览器核对后台与 H5
```

> 课程做法：**课堂演示保留 `dev-mode=true` 跑 33 项冒烟**（验收证据）；**正式发布前改回 `dev-mode=false`** 并重启，再按上面 5 条做生产验收，切换时同步 `CAMPUS_SMS_EXPOSE_CODE=false`。

上传验证截图参考：[后台素材上传](../screenshots/admin-file-upload.png)、[组织者封面上传](../screenshots/app-org-cover.png)。

---

## 附录 A 文件清单

| 文件 | 作用 |
| --- | --- |
| [`deploy/README.md`](../../deploy/README.md) | 资产总览、使用顺序、占位符清单、本地演练 |
| [`deploy/init-server.sh`](../../deploy/init-server.sh) | ECS 初始化（幂等，JDK 仓库/tar.gz 降级） |
| [`deploy/deploy.sh`](../../deploy/deploy.sh) | 发布/启停/状态（备份、同步 web、systemd、Nginx、健康检查） |
| [`deploy/flow/campus-activity-pipeline.yaml`](../../deploy/flow/campus-activity-pipeline.yaml) | 云效 YAML 流水线（4 个占位符） |
| `server/src/main/resources/db/schema.sql` / `data.sql` | 建表 / 演示数据 |
| `server/target/campus-activity-server-1.0.0.jar` | 后端产物（部署包内为 `app.jar`） |
| `admin/dist`、`app/dist/build/h5` | 前端产物（部署包内为 `web/admin`、`web/app`） |

## 附录 B 参考链接

- 什么是云效：<https://help.aliyun.com/zh/yunxiao/product-overview/what-is-cloud-effect>
- Java 应用构建并部署到 ECS 主机：<https://help.aliyun.com/zh/yunxiao/user-guide/build-and-deploy-a-java-application-to-an-ecs-host>
- YAML 流水线：<https://help.aliyun.com/zh/yunxiao/user-guide/yaml-preliminary-experience>
- YAML 步骤 steps 清单：<https://help.aliyun.com/zh/yunxiao/user-guide/step-steps-list>
- YAML 组件 component 清单：<https://help.aliyun.com/zh/yunxiao/user-guide/component-manifest>
- 云效主机组管理、ECS / 安全组 / 数字证书 / OSS / Redis：以阿里云官方文档当前版本为准
- Adoptium Temurin 21：<https://adoptium.net/temurin/releases/?version=21>

---

**最后核对日期：2026-11-11；核对人：教学包编写组。**
