#!/usr/bin/env bash
# ============================================================
# 校园活动小程序 · 服务器部署脚本（在 ECS 上执行，不要在本地开发机执行）
# ============================================================
# 用法：sh /opt/campus-activity/deploy.sh start|stop|restart|status
#
#   start    启动 systemd 服务并等待健康检查通过
#   stop     停止服务
#   restart  发布新版本（备份旧 jar → 同步 web/ → 重启 → reload Nginx → 健康检查）
#   status   查看服务状态、jar、web 目录与健康检查结果
#
# 制品包 package.tgz 根目录结构（与 07-附录 第 13 节一致）：
#   app.jar  web/admin/**  web/app/**  deploy.sh
#
# 支持两种调用方式：
#   A. 云效默认方式：package.tgz 解压到 /opt/campus-activity/，直接执行 /opt/campus-activity/deploy.sh
#   B. 预发目录方式（推荐，能精确保留旧 jar）：把 package.tgz 解压到 /opt/campus-activity/staging/，
#      执行 /opt/campus-activity/staging/deploy.sh restart
# 本脚本同时兼容 A、B 两种；A 方式下旧 jar 已被 tar 覆盖，回滚使用 releases/ 中上一次发布的快照。
#
# 可用环境变量覆盖（一般不用改）：
#   APP_HOME APP_USER APP_SERVICE APP_JAR WEB_DIR BACKUP_DIR NGINX_CONF
#   JAVA_BIN HEALTH_URL HEALTH_TIMEOUT KEEP_RELEASES SOURCE_DIR
# ============================================================

# 用 `sh deploy.sh` 调用时解释器可能是 dash，因此只使用 POSIX 语法（不写 pipefail、数组、[[ ]]）。
set -eu

APP_HOME="${APP_HOME:-/opt/campus-activity}"
APP_USER="${APP_USER:-campus}"
APP_SERVICE="${APP_SERVICE:-campus-activity}"
APP_JAR="${APP_JAR:-$APP_HOME/app.jar}"
WEB_DIR="${WEB_DIR:-$APP_HOME/web}"
BACKUP_DIR="${BACKUP_DIR:-$APP_HOME/releases}"
NGINX_CONF="${NGINX_CONF:-/etc/nginx/conf.d/campus-activity.conf}"
JAVA_BIN="${JAVA_BIN:-/usr/bin/java}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8016/api/ping}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-60}"
# 制品所在目录：默认脚本自身所在目录（云效把 package.tgz 解压到 /opt/campus-activity/ 后就是它）
SOURCE_DIR="${SOURCE_DIR:-$(cd "$(dirname "$0")" && pwd)}"

log()  { printf '[campus-deploy] %s\n' "$*"; }
warn() { printf '[campus-deploy][WARN] %s\n' "$*" >&2; }
die()  { printf '[campus-deploy][ERROR] %s\n' "$*" >&2; exit 1; }

# 云效主机部署默认以 root 执行；手工运维时也允许 sudo 执行
if [ "$(id -u)" -eq 0 ]; then
    SUDO=""
elif command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
else
    die "需要 root 权限：请用 root 执行，或安装 sudo 后重试"
fi

usage() {
    cat <<'EOF'
用法: sh deploy.sh <start|stop|restart|status>

  start    启动服务
  stop     停止服务
  restart  发布/重启（备份旧 jar、同步 web、重启、reload Nginx、健康检查）
  status   查看状态

例:
  sh /opt/campus-activity/deploy.sh restart
EOF
}

ensure_base() {
    [ -d "$APP_HOME" ] || $SUDO mkdir -p "$APP_HOME"
    $SUDO mkdir -p "$APP_HOME/uploads" "$APP_HOME/logs" "$BACKUP_DIR" "$WEB_DIR"
    if ! id -u "$APP_USER" >/dev/null 2>&1; then
        die "运行用户 $APP_USER 不存在，请先执行：sudo bash deploy/init-server.sh"
    fi
    if [ ! -x "$JAVA_BIN" ]; then
        die "未找到 Java 可执行文件 ${JAVA_BIN}，请先安装 JDK 21 或设置 JAVA_BIN 环境变量"
    fi
}

# 备份当前正在使用的 jar（只在「预发目录方式」下有效：此时新 jar 还没覆盖 APP_HOME/app.jar）
backup_running_jar() {
    if [ -f "$APP_JAR" ]; then
        rollback_file="$BACKUP_DIR/app.jar.rollback.$(date +%Y%m%d_%H%M%S)"
        $SUDO cp -f "$APP_JAR" "$rollback_file"
        log "已备份旧 jar: $rollback_file"
        prune_backups "app.jar.rollback.2*" 3
    fi
}

# 归档本次发布的 jar，回滚时按时间挑选版本
archive_release() {
    release_file="$BACKUP_DIR/app.jar.$(date +%Y%m%d_%H%M%S)"
    $SUDO cp -f "$APP_JAR" "$release_file"
    log "已归档本次发布: ${release_file}（保留最近 $KEEP_RELEASES 个）"
    prune_backups "app.jar.2*" "$KEEP_RELEASES"
}

# 只保留最近 N 个备份；文件名里没有空格，用 for 遍历即可
prune_backups() {
    keep_pattern="$1"
    keep_count="$2"
    backup_list="$(ls -1t "$BACKUP_DIR"/$keep_pattern 2>/dev/null || true)"
    [ -n "$backup_list" ] || return 0
    backup_index=0
    for backup_file in $backup_list; do
        backup_index=$((backup_index + 1))
        if [ "$backup_index" -gt "$keep_count" ]; then
            $SUDO rm -f "$backup_file"
        fi
    done
}

# 同步前端静态资源；有 rsync 时用 --delete 清掉旧 hash 文件，没有则整体替换目录
sync_web() {
    log "同步静态资源: $SOURCE_DIR/web -> $WEB_DIR"
    if command -v rsync >/dev/null 2>&1; then
        $SUDO rsync -a --delete "$SOURCE_DIR/web/" "$WEB_DIR/"
    else
        $SUDO rm -rf "$WEB_DIR/admin" "$WEB_DIR/app"
        $SUDO cp -a "$SOURCE_DIR/web/." "$WEB_DIR/"
    fi
}

fix_permissions() {
    # 运行用户需要可写上传与日志目录；静态资源与 jar 只读即可
    $SUDO chown -R "$APP_USER:$APP_USER" "$APP_HOME/uploads" "$APP_HOME/logs"
    $SUDO chmod 755 "$APP_HOME"
    $SUDO chmod -R a+rX "$WEB_DIR"
    if [ -f "$APP_JAR" ]; then
        $SUDO chmod 644 "$APP_JAR"
    fi
    if [ -f "$APP_HOME/deploy.sh" ]; then
        $SUDO chmod 755 "$APP_HOME/deploy.sh"
    fi
}

# 把制品同步到运行目录：兼容「直接解压」与「预发目录」两种方式
sync_release() {
    current_abs="$(cd "$APP_HOME" && pwd)"
    source_abs="$(cd "$SOURCE_DIR" && pwd)"
    jar_src=""
    if [ -f "$SOURCE_DIR/app.jar" ]; then
        jar_src="$SOURCE_DIR/app.jar"
    elif [ -f "$SOURCE_DIR/campus-activity-server-1.0.0.jar" ]; then
        # 兼容手工上传后端构建产物的情况
        jar_src="$SOURCE_DIR/campus-activity-server-1.0.0.jar"
    fi

    if [ -n "$jar_src" ] && [ "$source_abs" != "$current_abs" ]; then
        # 方式 B：制品在 staging，旧 jar 还在运行目录里，先备份再覆盖
        backup_running_jar
        log "复制新 jar: $jar_src -> $APP_JAR"
        $SUDO cp -f "$jar_src" "$APP_JAR"
        if [ -d "$SOURCE_DIR/web" ]; then
            sync_web
        fi
        if [ -f "$SOURCE_DIR/deploy.sh" ]; then
            $SUDO cp -f "$SOURCE_DIR/deploy.sh" "$APP_HOME/deploy.sh"
            log "已更新部署脚本: $APP_HOME/deploy.sh"
        fi
        archive_release
    elif [ -n "$jar_src" ]; then
        # 方式 A（云效默认）：package.tgz 已解压到运行目录，app.jar 与 web/ 已就位。
        # 注意：此时旧 app.jar 已被 tar 覆盖，无法再备份「正在运行的版本」；
        # 每次发布归档当前 jar 到 releases/，回滚使用上一次发布留下的快照。
        log "制品已解压到 ${APP_HOME}，使用当前 app.jar 与 web/"
        archive_release
    else
        # 没有新制品，只做重启（例如排障后重启服务）
        [ -f "$APP_JAR" ] || die "未找到 ${APP_JAR}，且 $SOURCE_DIR 下没有 app.jar / campus-activity-server-1.0.0.jar"
        log "未发现新制品，仅重启当前版本"
    fi
}

# 轮询 /api/ping，最多等 HEALTH_TIMEOUT 秒
health_check() {
    if ! command -v curl >/dev/null 2>&1; then
        warn "未安装 curl，跳过健康检查"
        return 0
    fi
    waited=0
    while [ "$waited" -lt "$HEALTH_TIMEOUT" ]; do
        if curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"code":0'; then
            log "健康检查通过: $HEALTH_URL"
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

reload_nginx() {
    if [ -f "$NGINX_CONF" ]; then
        if command -v nginx >/dev/null 2>&1; then
            NGINX_BIN="$(command -v nginx)"
        elif [ -x /usr/sbin/nginx ]; then
            NGINX_BIN=/usr/sbin/nginx
        else
            warn "未安装 Nginx，跳过 reload"
            return 0
        fi
        # nginx -t 先校验，失败立即退出（此时后端已重启，管道会标记本次发布失败）
        $SUDO "$NGINX_BIN" -t || die "nginx -t 校验失败，请修正 $NGINX_CONF 后手动 reload"
        $SUDO "$NGINX_BIN" -s reload
        log "Nginx 配置已 reload"
    else
        warn "未找到 ${NGINX_CONF}，跳过 Nginx reload（首次部署请先安装站点配置，见 06 号手册第 6 章）"
    fi
}

start_service() {
    ensure_base
    if $SUDO systemctl is-active --quiet "$APP_SERVICE"; then
        log "服务 $APP_SERVICE 已在运行"
    else
        log "启动服务: $APP_SERVICE"
        $SUDO systemctl daemon-reload
        $SUDO systemctl start "$APP_SERVICE"
    fi
    if ! health_check; then
        $SUDO journalctl -u "$APP_SERVICE" -n 80 --no-pager 2>/dev/null || true
        die "启动失败：服务未通过健康检查（${HEALTH_URL}）"
    fi
}

stop_service() {
    if $SUDO systemctl is-active --quiet "$APP_SERVICE"; then
        log "停止服务: $APP_SERVICE"
        $SUDO systemctl stop "$APP_SERVICE"
        log "服务已停止"
    else
        log "服务 $APP_SERVICE 当前未运行"
    fi
}

restart_service() {
    ensure_base
    sync_release
    fix_permissions
    $SUDO systemctl daemon-reload
    log "重启服务: $APP_SERVICE"
    $SUDO systemctl restart "$APP_SERVICE"
    if ! health_check; then
        warn "健康检查失败，最近 80 行日志如下："
        $SUDO journalctl -u "$APP_SERVICE" -n 80 --no-pager 2>/dev/null || true
        die "发布失败：服务未通过健康检查（${HEALTH_URL}）"
    fi
    reload_nginx
    log "发布完成 ✅ 可用 curl $HEALTH_URL 复核"
}

show_status() {
    printf '== 服务 ==\n'
    if $SUDO systemctl is-active --quiet "$APP_SERVICE"; then
        printf '服务状态: active (running)\n'
    else
        printf '服务状态: inactive/failed（可查看 journalctl -u %s -n 50）\n' "$APP_SERVICE"
    fi
    $SUDO systemctl status "$APP_SERVICE" --no-pager 2>/dev/null || true

    printf '\n== 制品 ==\n'
    if [ -f "$APP_JAR" ]; then
        ls -l "$APP_JAR"
    else
        printf '未找到 %s\n' "$APP_JAR"
    fi
    printf 'releases 最近 3 个快照:\n'
    ls -1t "$BACKUP_DIR"/app.jar.2* 2>/dev/null | head -n 3 || true

    printf '\n== 静态资源 ==\n'
    if [ -d "$WEB_DIR" ]; then
        ls -1 "$WEB_DIR" 2>/dev/null || true
    else
        printf '未找到 %s\n' "$WEB_DIR"
    fi

    printf '\n== 健康检查 ==\n'
    if command -v curl >/dev/null 2>&1; then
        if curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null; then
            printf '\n'
        else
            printf '健康检查失败: %s\n' "$HEALTH_URL"
        fi
    else
        printf '未安装 curl，跳过\n'
    fi
}

case "${1:-}" in
    start)   start_service ;;
    stop)    stop_service ;;
    restart) restart_service ;;
    status)  show_status ;;
    *)       usage; exit 1 ;;
esac
