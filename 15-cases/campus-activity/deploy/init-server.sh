#!/usr/bin/env bash
# ============================================================
# 校园活动小程序 · ECS 服务器初始化脚本（幂等，可重复执行）
# ============================================================
# 适用系统：Alibaba Cloud Linux 3（dnf）/ Ubuntu 22.04（apt），以 root 执行。
# 用法：
#   sudo bash deploy/init-server.sh                 # 真正执行
#   DRY_RUN=1 bash deploy/init-server.sh            # 本地演练：只打印命令，不改系统（无需 root）
#
# 本脚本负责：
#   1. 安装基础工具（curl/tar/rsync）、JDK 21、MySQL 8、Redis、Nginx
#   2. 创建运行用户 campus 与目录 /opt/campus-activity/{uploads,logs,releases,web}
#   3. 生成 /etc/campus-activity/env（从 deploy/env.example 复制，权限 600）
#   4. 安装 systemd unit 与 Nginx 站点配置（文件存在时）
#   5. 打印后续手工步骤：MySQL 安全初始化、建库导数据、编辑 env、首次启动
#
# 说明（重要）：
#   * JDK 21 在发行版仓库中的可得性不稳定：脚本「已有 21 跳过 → 尝试包管理器 →
#     尝试 Temurin tar.gz → 打印手工安装指引」，任何一步失败都不会让脚本直接退出。
#   * MySQL/Redis 版本随发行版仓库不同（本项目实测 MySQL 8.4.6、Redis 8.2.2；
#     发行版仓库通常是 MySQL 8.0、Redis 6.0/6.2；本项目只用 SET NX EX、Hash、Lua，6.0 即可跑通）。
#   * Redis 的 systemd 服务名不同发行版可能是 redis 或 redis-server，脚本两种都试。
#   * 数据库导入默认不自动执行（避免误清库），需要时用 IMPORT_DB=true 显式开启。
# ============================================================

set -euo pipefail

# ---------------- 可覆盖变量 ----------------
APP_DIR="${APP_DIR:-/opt/campus-activity}"
APP_USER="${APP_USER:-campus}"
ENV_DIR="${ENV_DIR:-/etc/campus-activity}"
ENV_FILE="${ENV_FILE:-$ENV_DIR/env}"
TIMEZONE="${TIMEZONE:-Asia/Shanghai}"

# JDK：优先复用 /opt/java21；可替换为阿里云 Dragonwell 21 的 tar.gz 地址
JAVA_HOME_DIR="${JAVA_HOME_DIR:-/opt/java21}"
JAVA_TARBALL_URL="${JAVA_TARBALL_URL:-https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse}"
AUTO_DOWNLOAD_JDK="${AUTO_DOWNLOAD_JDK:-true}"

# MySQL / Redis
MYSQL_SERVICE_PASSWORD_HINT="${MYSQL_SERVICE_PASSWORD_HINT:-}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"          # 设置后脚本会尝试写入 Redis 配置并重启
REDIS_PORT="${REDIS_PORT:-6379}"

# 数据库导入（默认关闭）：IMPORT_DB=true MYSQL_ROOT_PASSWORD=... SQL_DIR=/root/db
IMPORT_DB="${IMPORT_DB:-false}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
SQL_DIR="${SQL_DIR:-}"
DB_NAME="${DB_NAME:-campus_activity}"
DB_USER="${DB_USER:-campus}"
DB_PASSWORD="${DB_PASSWORD:-}"

# 本地演练开关
DRY_RUN="${DRY_RUN:-0}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---------------- 输出与执行封装 ----------------
log()  { printf '\033[32m[init]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[init][WARN]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31m[init][ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

# run：DRY_RUN=1 时只打印，不执行
run() {
    if [ "$DRY_RUN" = "1" ]; then
        printf '[dry-run] %s\n' "$*"
    else
        "$@"
    fi
}

have() { command -v "$1" >/dev/null 2>&1; }

# ---------------- 前置检查 ----------------
if [ "$DRY_RUN" != "1" ] && [ "$(id -u)" -ne 0 ]; then
    die "请用 root 执行：sudo bash deploy/init-server.sh（本地演练可加 DRY_RUN=1）"
fi

detect_os() {
    if [ ! -r /etc/os-release ]; then
        if [ "$DRY_RUN" = "1" ]; then
            OS_FAMILY="dnf"
            log "[dry-run] 未检测到 /etc/os-release，按 Alibaba Cloud Linux 3（dnf）分支演示"
            return 0
        fi
        die "无法识别操作系统：缺少 /etc/os-release"
    fi
    # shellcheck disable=SC1091
    . /etc/os-release
    OS_ID="${ID:-unknown}"
    case "$OS_ID" in
        alinux|alios|centos|rhel|rocky|anolis|alinux3) OS_FAMILY="dnf" ;;
        ubuntu|debian) OS_FAMILY="apt" ;;
        *) die "本脚本支持 Alibaba Cloud Linux 3 / Ubuntu 22.04，当前系统：${PRETTY_NAME:-$OS_ID}" ;;
    esac
    log "操作系统：${PRETTY_NAME:-$OS_ID}（包管理器：${OS_FAMILY}）"
}

pkg_install() {
    if [ "$OS_FAMILY" = "dnf" ]; then
        run dnf install -y "$@"
    else
        run env DEBIAN_FRONTEND=noninteractive apt-get install -y "$@"
    fi
}

# 按候选顺序启动并设置开机自启，返回 0 表示成功
enable_service() {
    if [ "$DRY_RUN" = "1" ]; then
        log "[dry-run] systemctl enable --now $*"
        return 0
    fi
    for svc in "$@"; do
        if systemctl cat "$svc.service" >/dev/null 2>&1; then
            systemctl enable --now "$svc" && return 0
        fi
    done
    return 1
}

# ---------------- 1. 基础工具 ----------------
setup_base_packages() {
    log "安装基础工具：curl tar rsync（已安装则跳过）"
    for tool in curl tar rsync; do
        if [ "$DRY_RUN" = "1" ]; then
            log "[dry-run] 检查/安装基础工具 $tool"
            continue
        fi
        if ! have "$tool"; then
            pkg_install "$tool" || warn "安装 $tool 失败，请稍后手工安装"
        fi
    done
    # 时区统一 Asia/Shanghai，避免活动时间、签到窗口偏移
    if have timedatectl && [ "$DRY_RUN" != "1" ]; then
        run timedatectl set-timezone "$TIMEZONE"
        log "系统时区：$(timedatectl show -p Timezone --value 2>/dev/null || echo "$TIMEZONE")"
    fi
}

# ---------------- 2. JDK 21 ----------------
is_jdk21() {
    have java && java -version 2>&1 | grep -q 'version "21'
}

write_java_profile() {
    if [ -f /etc/profile.d/java21.sh ] && grep -q "$JAVA_HOME_DIR" /etc/profile.d/java21.sh; then
        log "/etc/profile.d/java21.sh 已存在且指向 $JAVA_HOME_DIR"
        return 0
    fi
    if [ "$DRY_RUN" = "1" ]; then
        log "[dry-run] 写入 /etc/profile.d/java21.sh（JAVA_HOME=${JAVA_HOME_DIR}）"
        return 0
    fi
    cat > /etc/profile.d/java21.sh <<EOF
# JDK 21（由 deploy/init-server.sh 生成）
export JAVA_HOME=$JAVA_HOME_DIR
export PATH=\$JAVA_HOME/bin:\$PATH
EOF
    log "已写入 /etc/profile.d/java21.sh"
}

print_java_manual_guide() {
    cat <<'EOF'

---------------- JDK 21 手工安装指引 ----------------
未能在发行版仓库或自动下载中拿到 JDK 21，这不影响后续步骤（可先继续，装好后再启动后端）。
二选一：

【方式一】Temurin 21（推荐，官方构建）
  cd /tmp
  curl -fL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  sudo mkdir -p /opt/java21
  sudo tar xzf jdk21.tar.gz -C /opt/java21 --strip-components=1
  sudo tee /etc/profile.d/java21.sh >/dev/null <<'PROFILE'
  export JAVA_HOME=/opt/java21
  export PATH=$JAVA_HOME/bin:$PATH
PROFILE
  sudo ln -sf /opt/java21/bin/java /usr/bin/java
  java -version        # 期望 openjdk version "21.x"

【方式二】阿里云 Dragonwell 21
  下载地址以 Dragonwell 官网当前版本为准（Alibaba Dragonwell 21 x64 Linux tar.gz），
  解压到 /opt/java21 后执行与方式一相同的 profile 与软链命令。

安装完成后重新执行本脚本即可（幂等）：sudo bash deploy/init-server.sh
------------------------------------------------------
EOF
}

install_java_from_tarball() {
    if [ "$DRY_RUN" = "1" ]; then
        log "[dry-run] 下载 JDK 21 tar.gz -> 解压到 ${JAVA_HOME_DIR} -> 写 /etc/profile.d/java21.sh -> 软链 /usr/bin/java"
        return 0
    fi
    if [ -x "$JAVA_HOME_DIR/bin/java" ]; then
        log "复用已解压的 JDK：$JAVA_HOME_DIR"
    else
        if [ "$AUTO_DOWNLOAD_JDK" != "true" ]; then
            print_java_manual_guide
            return 0
        fi
        if ! have curl; then
            warn "未安装 curl，无法自动下载 JDK"
            print_java_manual_guide
            return 0
        fi
        log "尝试下载 Temurin 21 tar.gz（地址可用 JAVA_TARBALL_URL 覆盖）"
        tmp_tar="${TMPDIR:-/tmp}/campus-jdk21.tar.gz"
        if run curl -fL --connect-timeout 15 -m 600 -o "$tmp_tar" "$JAVA_TARBALL_URL"; then
            run mkdir -p "$JAVA_HOME_DIR"
            if run tar xzf "$tmp_tar" -C "$JAVA_HOME_DIR" --strip-components=1; then
                log "JDK 已解压到 $JAVA_HOME_DIR"
            else
                warn "JDK 压缩包解压失败（下载地址可能已过期）"
            fi
            run rm -f "$tmp_tar"
        else
            warn "JDK 下载失败（网络受限或下载地址过期）"
        fi
    fi

    if [ -x "$JAVA_HOME_DIR/bin/java" ]; then
        write_java_profile
        if [ "$DRY_RUN" != "1" ]; then
            ln -sf "$JAVA_HOME_DIR/bin/java" /usr/bin/java
            log "已创建软链 /usr/bin/java -> $JAVA_HOME_DIR/bin/java"
        fi
        log "JDK 版本：$("$JAVA_HOME_DIR/bin/java" -version 2>&1 | head -n 1)"
    else
        warn "仍未获得 JDK 21，请按下方指引手工安装"
        print_java_manual_guide
    fi
}

setup_java() {
    log "检查 JDK 21"
    if is_jdk21; then
        log "已检测到 JDK 21：$(java -version 2>&1 | head -n 1)"
        return 0
    fi
    log "未检测到 JDK 21，先尝试发行版仓库（注意：不同发行版/镜像不一定提供 java-21-openjdk）"
    if [ "$OS_FAMILY" = "dnf" ]; then
        pkg_install java-21-openjdk-devel >/dev/null 2>&1 || pkg_install java-21-openjdk-headless >/dev/null 2>&1 || true
    else
        pkg_install openjdk-21-jdk-headless >/dev/null 2>&1 || true
    fi
    if is_jdk21; then
        log "发行版仓库安装 JDK 21 成功"
        return 0
    fi
    warn "发行版仓库没有可用的 JDK 21（属常见情况），改用 tar.gz 方案"
    install_java_from_tarball
}

# ---------------- 3. MySQL 8 ----------------
setup_mysql() {
    log "检查 MySQL"
    if have mysqladmin || have mysqld; then
        log "MySQL 已安装"
    else
        log "安装 MySQL 8（发行版仓库版本以实际为准：ALinux3/Ubuntu 22.04 通常为 8.0.x）"
        if [ "$OS_FAMILY" = "dnf" ]; then
            pkg_install mysql-server mysql || warn "MySQL 安装失败，请检查软件源或改用云数据库 RDS"
        else
            pkg_install mysql-server || warn "MySQL 安装失败，请检查软件源"
        fi
    fi

    if enable_service mysql mysqld; then
        log "MySQL 服务已启动并设为开机自启"
    else
        warn "未能自动启动 MySQL，请手动确认服务名（mysql 或 mysqld）"
    fi

    if [ "$DRY_RUN" != "1" ]; then
        waited=0
        while [ "$waited" -lt 30 ]; do
            if mysqladmin ping >/dev/null 2>&1; then
                log "MySQL 已就绪"
                break
            fi
            sleep 2
            waited=$((waited + 2))
        done
    fi

    cat <<'EOF'
---------------- MySQL 安全初始化（手工，必须做） ----------------
  # Ubuntu 22.04 默认 root 走 auth_socket，先切到 MySQL 用户再设密码：
  sudo mysql
  # Alibaba Cloud Linux 3 可直连：
  # mysql -uroot
  -- 在 MySQL 里执行：
  ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '替换为强密码';
  DELETE FROM mysql.user WHERE user='';
  DROP DATABASE IF EXISTS test;
  FLUSH PRIVILEGES;
  exit;
  # 验证：
  MYSQL_PWD='替换为强密码' mysql -uroot -e "SELECT VERSION(), @@global.time_zone;"
  # 建议在 my.cnf 的 [mysqld] 中加入 default-time-zone='+08:00' 后重启
------------------------------------------------------------------
EOF
    if [ -n "$MYSQL_SERVICE_PASSWORD_HINT" ]; then
        warn "如果需要脚本记录密码，请把 MYSQL_ROOT_PASSWORD 通过 IMPORT_DB=true 显式传入（不要在命令行回显）"
    fi
}

# ---------------- 4. Redis ----------------
setup_redis() {
    log "检查 Redis"
    if have redis-server || have redis-cli; then
        log "Redis 已安装"
    else
        log "安装 Redis（发行版仓库常见 6.0/6.2；本项目建议 6.2+，6.0 也可跑通）"
        if [ "$OS_FAMILY" = "dnf" ]; then
            pkg_install redis || warn "Redis 安装失败"
        else
            pkg_install redis-server || warn "Redis 安装失败"
        fi
    fi

    # 服务名兼容：RHEL 系是 redis，Debian/Ubuntu 是 redis-server
    if enable_service redis redis-server; then
        log "Redis 服务已启动并设为开机自启"
    else
        warn "未能自动启动 Redis；请按发行版确认服务名：systemctl status redis-server 或 systemctl status redis"
    fi

    # 找到配置文件：不同发行版路径不同
    redis_conf=""
    for candidate in /etc/redis/redis.conf /etc/redis.conf; do
        if [ -f "$candidate" ]; then
            redis_conf="$candidate"
            break
        fi
    done

    if [ -z "$redis_conf" ]; then
        warn "未找到 Redis 配置文件（/etc/redis/redis.conf 或 /etc/redis.conf），请手工配置密码与 bind"
        return 0
    fi

    if [ -n "$REDIS_PASSWORD" ]; then
        log "写入 Redis 密码与 bind 127.0.0.1（配置文件：${redis_conf}）"
        if [ "$DRY_RUN" = "1" ]; then
            log "[dry-run] sed 修改 requirepass/bind 并重启 Redis"
        else
            # requirepass 可能出现被注释或已有值两种情况，统一切成指定值；没有则追加
            if grep -Eq '^[[:space:]]*#?[[:space:]]*requirepass ' "$redis_conf"; then
                sed -i -E "s|^[[:space:]]*#?[[:space:]]*requirepass .*|requirepass ${REDIS_PASSWORD}|" "$redis_conf"
            else
                printf '\nrequirepass %s\n' "$REDIS_PASSWORD" >> "$redis_conf"
            fi
            # bind 只监听本机，避免 6379 暴露公网
            if grep -Eq '^[[:space:]]*bind ' "$redis_conf"; then
                sed -i -E 's|^[[:space:]]*bind .*|bind 127.0.0.1|' "$redis_conf"
            else
                printf '\nbind 127.0.0.1\n' >> "$redis_conf"
            fi
            enable_service redis redis-server >/dev/null 2>&1 || true
            systemctl restart redis 2>/dev/null || systemctl restart redis-server 2>/dev/null || true
            sleep 1
            if redis-cli -a "$REDIS_PASSWORD" --no-auth-warning -n 11 ping 2>/dev/null | grep -q PONG; then
                log "Redis 密码配置成功，PONG"
            else
                warn "Redis 重启后 PING 未通过，请检查配置文件与密码中的特殊字符（sed 转义）"
            fi
        fi
    else
        cat <<EOF
---------------- Redis 手工安全配置（必须做） ----------------
  配置文件：$redis_conf
  sudo sed -i -E 's|^[[:space:]]*#?[[:space:]]*requirepass .*|requirepass 替换为强密码|' "$redis_conf"
  sudo sed -i -E 's|^[[:space:]]*bind .*|bind 127.0.0.1|' "$redis_conf"
  sudo systemctl restart redis 2>/dev/null || sudo systemctl restart redis-server
  redis-cli -a '替换为强密码' --no-auth-warning -n 11 ping     # 期望 PONG
  （下次可带 REDIS_PASSWORD=... 重跑本脚本让脚本代为修改）
----------------------------------------------------------------
EOF
    fi
}

# ---------------- 5. Nginx ----------------
setup_nginx() {
    log "检查 Nginx"
    if ! have nginx; then
        pkg_install nginx || warn "Nginx 安装失败"
    fi
    if enable_service nginx; then
        log "Nginx 已启动并设为开机自启"
    else
        warn "Nginx 未能启动，请检查 nginx -t 输出"
    fi

    site_conf_src="$SCRIPT_DIR/nginx/campus-activity.conf"
    site_conf_dst="/etc/nginx/conf.d/campus-activity.conf"
    if [ -f "$site_conf_src" ]; then
        run cp -f "$site_conf_src" "$site_conf_dst"
        log "已安装站点配置：${site_conf_dst}（记得把 server_name 改成真实域名/IP）"
        if [ "$DRY_RUN" != "1" ] && have nginx; then
            if nginx -t >/dev/null 2>&1; then
                run nginx -s reload
                log "Nginx 配置校验通过并已 reload"
            else
                warn "nginx -t 校验失败，请检查 ${site_conf_dst}（常见：server_name 重复 / 端口占用）"
                nginx -t || true
            fi
        fi
    else
        warn "未找到 ${site_conf_src}；请从仓库 deploy/nginx/ 上传站点配置到 $site_conf_dst"
    fi

    if [ -d /etc/nginx/sites-enabled ] && [ -e /etc/nginx/sites-enabled/default ]; then
        warn "Ubuntu 默认站点 /etc/nginx/sites-enabled/default 仍启用，会抢占 80 端口默认响应"
        warn "  可执行：sudo rm -f /etc/nginx/sites-enabled/default && sudo nginx -t && sudo systemctl reload nginx"
        warn "  或把 campus-activity.conf 的 server_name 改为 _（用 IP 访问）"
    fi
}

# ---------------- 6. 运行用户、目录、env、systemd ----------------
setup_app() {
    log "创建运行用户与目录"
    if id -u "$APP_USER" >/dev/null 2>&1; then
        log "用户 $APP_USER 已存在"
    else
        if [ "$DRY_RUN" = "1" ]; then
            log "[dry-run] useradd --system --create-home --shell /usr/sbin/nologin $APP_USER"
        else
            useradd --system --create-home --shell /usr/sbin/nologin "$APP_USER"
            log "已创建系统用户：$APP_USER"
        fi
    fi

    run mkdir -p "$APP_DIR" "$APP_DIR/uploads" "$APP_DIR/logs" "$APP_DIR/releases" "$APP_DIR/web"
    run mkdir -p "$ENV_DIR"
    if [ "$DRY_RUN" != "1" ]; then
        chown -R "$APP_USER:$APP_USER" "$APP_DIR/uploads" "$APP_DIR/logs"
        chmod 755 "$APP_DIR"
    fi
    log "应用目录：${APP_DIR}（uploads/logs 归 $APP_USER 所有，jar 与 web 只读）"

    # 环境变量文件：存在则不动，避免覆盖生产密钥
    if [ -f "$ENV_FILE" ]; then
        log "$ENV_FILE 已存在，保持不动（如需重置请手工删除后重跑）"
    elif [ -f "$SCRIPT_DIR/env.example" ]; then
        run cp -f "$SCRIPT_DIR/env.example" "$ENV_FILE"
        run chmod 600 "$ENV_FILE"
        run chown root:root "$ENV_FILE"
        warn "已生成 ${ENV_FILE}，请编辑其中的密码与密钥后再启动服务（见 06 号手册 5.3）"
    else
        warn "未找到 $SCRIPT_DIR/env.example，请手工创建 ${ENV_FILE}（模板见仓库 deploy/env.example）"
    fi
    if [ "$DRY_RUN" != "1" ]; then
        chmod 600 "$ENV_FILE" 2>/dev/null || true
        chown root:root "$ENV_FILE" 2>/dev/null || true
    fi

    # systemd unit
    unit_src="$SCRIPT_DIR/campus-activity.service"
    unit_dst="/etc/systemd/system/campus-activity.service"
    if [ -f "$unit_src" ]; then
        run cp -f "$unit_src" "$unit_dst"
        if [ "$DRY_RUN" != "1" ]; then
            systemctl daemon-reload
            systemctl enable campus-activity >/dev/null 2>&1 || true
        fi
        log "已安装 systemd unit：${unit_dst}（已 enable，编辑 env 后再 start）"
    else
        warn "未找到 ${unit_src}，请手工安装 campus-activity.service"
    fi
}

# ---------------- 7. 可选：建库导数据 ----------------
print_db_manual_guide() {
    cat <<EOF

---------------- 数据库初始化（手工，或在服务器上重跑脚本时加 IMPORT_DB=true） ----------------
  1) 上传 SQL：把仓库 server/src/main/resources/db/{schema.sql,data.sql} 上传到服务器，例如 /root/db/
  2) 建库：
     MYSQL_PWD='root密码' mysql -uroot -e "CREATE DATABASE IF NOT EXISTS $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  3) 导入（schema.sql 含 DROP TABLE，只在初始化时执行；生产禁止重跑）：
     MYSQL_PWD='root密码' mysql -uroot "$DB_NAME" < /root/db/schema.sql
     MYSQL_PWD='root密码' mysql -uroot "$DB_NAME" < /root/db/data.sql
  4) 建专用账号（最小权限）：
     MYSQL_PWD='root密码' mysql -uroot <<'SQL'
     CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '替换为专用强密码';
     GRANT SELECT, INSERT, UPDATE, DELETE ON $DB_NAME.* TO '$DB_USER'@'localhost';
     FLUSH PRIVILEGES;
     SQL
  5) 校验 12 张表：
     MYSQL_PWD='$DB_USER 密码' mysql -u$DB_USER "$DB_NAME" \\
       -e "SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema='$DB_NAME';"
  6) 把账号密码写入 $ENV_FILE 的 MYSQL_USERNAME / MYSQL_PASSWORD
------------------------------------------------------------------------------------------
EOF
}

maybe_import_db() {
    if [ "$IMPORT_DB" != "true" ]; then
        print_db_manual_guide
        return 0
    fi
    [ -n "$MYSQL_ROOT_PASSWORD" ] || die "IMPORT_DB=true 时必须提供 MYSQL_ROOT_PASSWORD"
    [ -n "$SQL_DIR" ] || die "IMPORT_DB=true 时必须提供 SQL_DIR（包含 schema.sql 与 data.sql）"
    [ -f "$SQL_DIR/schema.sql" ] || die "未找到 $SQL_DIR/schema.sql"
    [ -f "$SQL_DIR/data.sql" ] || die "未找到 $SQL_DIR/data.sql"
    [ -n "$DB_PASSWORD" ] || die "IMPORT_DB=true 时必须提供 DB_PASSWORD（应用专用账号密码）"

    log "建库并导入 SQL（${SQL_DIR}）"
    if [ "$DRY_RUN" = "1" ]; then
        log "[dry-run] 建库 ${DB_NAME}、导入 schema.sql/data.sql、创建账号 $DB_USER"
        return 0
    fi
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot \
        -e "CREATE DATABASE IF NOT EXISTS $DB_NAME DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$DB_NAME" < "$SQL_DIR/schema.sql"
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$DB_NAME" < "$SQL_DIR/data.sql"
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON $DB_NAME.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL
    table_count="$(MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B \
        -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
    log "数据库 $DB_NAME 已就绪，表数量：${table_count}（期望 12）"
}

# ---------------- 8. 完成汇总 ----------------
print_summary() {
    printf '\n================ 初始化完成 ================\n'
    printf '运行用户 : %s\n' "$APP_USER"
    printf '应用目录 : %s\n' "$APP_DIR"
    printf '环境文件 : %s（权限 600，请编辑密码与密钥）\n' "$ENV_FILE"
    printf 'systemd  : /etc/systemd/system/campus-activity.service（已 enable，未 start）\n'
    printf 'Nginx    : /etc/nginx/conf.d/campus-activity.conf\n'
    printf '\n下一步：\n'
    printf '  1. 编辑 %s：改 MySQL/Redis 密码、JWT/签到密钥、dev-mode=false、expose-code=false\n' "$ENV_FILE"
    printf '  2. 完成 MySQL 安全初始化与建库导数据（见上方输出或 06 号手册第 4 章）\n'
    printf '  3. 安装/上传应用：把 app.jar 与 web/ 放到 %s，然后 sudo systemctl start campus-activity\n' "$APP_DIR"
    printf '  4. 安装 Nginx 站点配置后：sudo nginx -t && sudo nginx -s reload\n'
    printf '  5. 健康检查：curl -s http://127.0.0.1:8016/api/ping\n'
    printf '  6. 查看日志：sudo journalctl -u campus-activity -f\n'
    printf '============================================\n'
}

main() {
    log "校园活动小程序服务器初始化开始（DRY_RUN=${DRY_RUN}）"
    detect_os
    setup_base_packages
    setup_java
    setup_mysql
    setup_redis
    setup_nginx
    setup_app
    maybe_import_db
    print_summary
    log "初始化流程结束。JDK/MySQL/Redis/Nginx 任何一项显示 WARN，请按提示手工补齐后重跑本脚本（幂等）。"
}

main "$@"
