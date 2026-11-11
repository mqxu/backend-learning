#!/usr/bin/env bash
# 端到端冒烟测试：登录 -> 建活动 -> 审核 -> 报名 -> 满员候补 -> 签到 -> 统计
# 扩展能力：短信验证码登录 -> 登出黑名单 -> 文件上传与静态访问
# 用法：./scripts/smoke.sh（BASE 可覆盖，默认 http://127.0.0.1:8016/api）
# 依赖：后端已启动、MySQL/Redis 可用，本机有 curl 与 jq

set -uo pipefail

BASE=${BASE:-http://127.0.0.1:8016/api}
SERVER=${BASE%/api}
PASS=0
FAIL=0

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red() { printf "\033[31m%s\033[0m\n" "$1"; }

check() {
  local name=$1 expected=$2 actual=$3
  if [ "$expected" = "$actual" ]; then
    green "  ✓ ${name}"
    PASS=$((PASS + 1))
  else
    red "  ✗ ${name}（期望 ${expected}，实际 ${actual}）"
    FAIL=$((FAIL + 1))
  fi
}

login() {
  curl -s --noproxy '*' -X POST "$BASE/app/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"devOpenId\":\"$1\"}" | jq -r '.data.token'
}

bind() {
  curl -s --noproxy '*' -X POST "$BASE/app/auth/bind" \
    -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"studentNo\":\"$2\",\"realName\":\"$3\"}" | jq -r '.code'
}

echo "== 0. 健康检查 =="
HEALTH=$(curl -s --noproxy '*' --max-time 5 "$BASE/ping" | jq -r '.code')
if [ "$HEALTH" != "0" ]; then
  red "后端未启动，请先运行 mvn spring-boot:run"
  exit 1
fi
green "  ✓ /api/ping 正常"

echo "== 1. 学生登录与绑定 =="
A_TOKEN=$(login mock-student-1)
check "学生 A 登录拿到 token" "true" "$([ -n "$A_TOKEN" ] && echo true || echo false)"
check "学生 A 绑定学号" "0" "$(bind "$A_TOKEN" 2023001 张小明)"

B_TOKEN=$(login mock-student-2)
check "学生 B 登录拿到 token" "true" "$([ -n "$B_TOKEN" ] && echo true || echo false)"
check "学生 B 绑定学号" "0" "$(bind "$B_TOKEN" 2023002 李小红)"

echo "== 2. 组织者创建活动 =="
O_TOKEN=$(login mock-organizer-1)
START=$(date -v+25M +"%Y-%m-%d %H:%M:%S" 2>/dev/null || date -d '+25 minutes' +"%Y-%m-%d %H:%M:%S")
END=$(date -v+150M +"%Y-%m-%d %H:%M:%S" 2>/dev/null || date -d '+150 minutes' +"%Y-%m-%d %H:%M:%S")
SIGNUP_START=$(date +"%Y-%m-%d %H:%M:%S")
SIGNUP_END=$(date -v+20M +"%Y-%m-%d %H:%M:%S" 2>/dev/null || date -d '+20 minutes' +"%Y-%m-%d %H:%M:%S")

CREATE=$(curl -s --noproxy '*' -X POST "$BASE/org/activities" \
  -H "Authorization: Bearer $O_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"冒烟测试活动 $(date +%H%M%S)\",\"categoryId\":1001,\"summary\":\"自动化脚本创建\",\"content\":\"用于验证主链路\",\"startTime\":\"$START\",\"endTime\":\"$END\",\"signupStartTime\":\"$SIGNUP_START\",\"signupEndTime\":\"$SIGNUP_END\",\"locationName\":\"大学生活动中心 报告厅\",\"latitude\":32.11,\"longitude\":118.92,\"capacity\":1,\"waitlistEnabled\":1,\"auditRequired\":0,\"checkinType\":1}")

AID=$(echo "$CREATE" | jq -r '.data.id')
check "创建活动返回 id" "0" "$(echo "$CREATE" | jq -r '.code')"

echo "== 3. 提交审核与管理员审核 =="
check "提交审核" "0" "$(curl -s --noproxy '*' -X POST "$BASE/org/activities/$AID/submit" -H "Authorization: Bearer $O_TOKEN" | jq -r '.code')"

ADMIN_TOKEN=$(curl -s --noproxy '*' -X POST "$BASE/admin/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
check "管理员登录" "true" "$([ -n "$ADMIN_TOKEN" ] && echo true || echo false)"

AUDIT_LIST_CODE=$(curl -s --noproxy '*' "$BASE/admin/activities/audit-list?page=1&size=20" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r --arg id "$AID" '.data.records[] | select(.id == ($id | tonumber)) | .statusLabel')
check "待审核列表包含新活动" "待审核" "$AUDIT_LIST_CODE"

check "审核通过" "0" "$(curl -s --noproxy '*' -X POST "$BASE/admin/activities/$AID/audit" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"pass":true}' | jq -r '.code')"

echo "== 4. 报名与名额控制 =="
SIGNUP_A=$(curl -s --noproxy '*' -X POST "$BASE/app/activities/$AID/signup" \
  -H "Authorization: Bearer $A_TOKEN" -H 'Content-Type: application/json' -d '{}')
check "学生 A 报名成功" "报名成功" "$(echo "$SIGNUP_A" | jq -r '.data.statusLabel')"

REPEAT=$(curl -s --noproxy '*' -X POST "$BASE/app/activities/$AID/signup" \
  -H "Authorization: Bearer $A_TOKEN" -H 'Content-Type: application/json' -d '{}')
check "学生 A 重复报名被拒绝" "30002" "$(echo "$REPEAT" | jq -r '.code')"

SIGNUP_B=$(curl -s --noproxy '*' -X POST "$BASE/app/activities/$AID/signup" \
  -H "Authorization: Bearer $B_TOKEN" -H 'Content-Type: application/json' -d '{}')
check "学生 B 名额满后进入候补" "候补中" "$(echo "$SIGNUP_B" | jq -r '.data.statusLabel')"

echo "== 5. 签到 =="
QR=$(curl -s --noproxy '*' "$BASE/org/activities/$AID/checkin-qr" -H "Authorization: Bearer $O_TOKEN")
check "获取签到码" "0" "$(echo "$QR" | jq -r '.code')"
check "签到码轮换周期 30 秒" "30" "$(echo "$QR" | jq -r '.data.refreshSeconds')"
C_TOKEN=$(echo "$QR" | jq -r '.data.token')

CHECKIN=$(curl -s --noproxy '*' -X POST "$BASE/app/checkin" \
  -H "Authorization: Bearer $A_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"token\":\"$C_TOKEN\",\"latitude\":32.11,\"longitude\":118.92}")
check "学生 A 签到成功" "0" "$(echo "$CHECKIN" | jq -r '.code')"

CHECKIN_AGAIN=$(curl -s --noproxy '*' -X POST "$BASE/app/checkin" \
  -H "Authorization: Bearer $A_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"token\":\"$C_TOKEN\",\"latitude\":32.11,\"longitude\":118.92}")
check "重复签到被拒绝" "40003" "$(echo "$CHECKIN_AGAIN" | jq -r '.code')"

BAD_TOKEN=$(curl -s --noproxy '*' -X POST "$BASE/app/checkin" \
  -H "Authorization: Bearer $A_TOKEN" -H 'Content-Type: application/json' \
  -d '{"token":"9999.1.deadbeefdeadbeef","latitude":32.11,"longitude":118.92}')
check "伪造签到码被拒绝" "40001" "$(echo "$BAD_TOKEN" | jq -r '.code')"

echo "== 6. 统计 =="
STAT=$(curl -s --noproxy '*' "$BASE/org/activities/$AID/stat" -H "Authorization: Bearer $O_TOKEN")
check "已签到人数为 1" "1" "$(echo "$STAT" | jq -r '.data.checkedInCount')"
check "候补人数为 1" "1" "$(echo "$STAT" | jq -r '.data.waitlistCount')"
check "到场率 100" "100" "$(echo "$STAT" | jq -r '.data.attendanceRate | floor')"

echo "== 7. 扩展能力：短信验证码登录 / 登出黑名单 / 文件上传 =="

# 短信验证码：Mock 通道且 expose-code=true 时验证码直接回传，脚本不需要翻日志
# 每次运行先清掉该手机号的验证码限流 key，保证脚本可重复执行
SMS_PHONE=${SMS_PHONE:-13800000003}
redis-cli -a "${REDIS_PASSWORD:-abc123}" --no-auth-warning -n "${REDIS_DATABASE:-11}" \
  del "campus:sms:interval:$SMS_PHONE" "campus:sms:code:$SMS_PHONE" \
      "campus:sms:daily:$SMS_PHONE" "campus:sms:fail:$SMS_PHONE" >/dev/null 2>&1 || true

SMS_SEND=$(curl -s --noproxy '*' -X POST "$BASE/app/auth/sms/send-code" \
  -H 'Content-Type: application/json' -d "{\"phone\":\"$SMS_PHONE\"}")
check "发送短信验证码" "0" "$(echo "$SMS_SEND" | jq -r '.code')"
SMS_CODE=$(echo "$SMS_SEND" | jq -r '.data.mockCode')
check "Mock 通道回传验证码" "true" "$([ -n "$SMS_CODE" ] && [ "$SMS_CODE" != "null" ] && echo true || echo false)"

# 先测错误验证码（验证码尚未被使用），再测正确验证码登录
SMS_BAD=$(curl -s --noproxy '*' -X POST "$BASE/app/auth/sms/login" \
  -H 'Content-Type: application/json' -d "{\"phone\":\"$SMS_PHONE\",\"code\":\"000000\"}")
check "错误验证码被拒绝" "60005" "$(echo "$SMS_BAD" | jq -r '.code')"

SMS_LOGIN=$(curl -s --noproxy '*' -X POST "$BASE/app/auth/sms/login" \
  -H 'Content-Type: application/json' -d "{\"phone\":\"$SMS_PHONE\",\"code\":\"$SMS_CODE\"}")
check "手机号验证码登录" "0" "$(echo "$SMS_LOGIN" | jq -r '.code')"
SMS_TOKEN=$(echo "$SMS_LOGIN" | jq -r '.data.token')
check "手机号登录返回 token" "true" "$([ -n "$SMS_TOKEN" ] && echo true || echo false)"

SMS_PROFILE=$(curl -s --noproxy '*' "$BASE/app/user/profile" -H "Authorization: Bearer $SMS_TOKEN")
check "手机号登录命中种子用户张小明" "张小明" "$(echo "$SMS_PROFILE" | jq -r '.data.nickname')"

SMS_REPEAT=$(curl -s --noproxy '*' -X POST "$BASE/app/auth/sms/send-code" \
  -H 'Content-Type: application/json' -d "{\"phone\":\"$SMS_PHONE\"}")
check "60 秒内重复发送被限流" "60002" "$(echo "$SMS_REPEAT" | jq -r '.code')"

# 登出：token 进 Redis 黑名单，同一 token 再用应 401
check "登出成功" "0" "$(curl -s --noproxy '*' -X POST "$BASE/app/auth/logout" \
  -H "Authorization: Bearer $SMS_TOKEN" | jq -r '.code')"
check "登出后 token 失效" "10001" "$(curl -s --noproxy '*' "$BASE/app/user/profile" \
  -H "Authorization: Bearer $SMS_TOKEN" | jq -r '.code')"

# 文件上传：本地存储返回 /uploads/<key>，上传后可匿名访问
TMP_PNG=$(mktemp -t campus-smoke-XXXXXX.png)
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDRsmoke' > "$TMP_PNG"
UPLOAD=$(curl -s --noproxy '*' -X POST "$BASE/files/upload?bizType=activity-cover" \
  -H "Authorization: Bearer $A_TOKEN" \
  -F "file=@$TMP_PNG;filename=smoke-cover.png;type=image/png")
check "上传活动封面" "0" "$(echo "$UPLOAD" | jq -r '.code')"
UPLOAD_URL=$(echo "$UPLOAD" | jq -r '.data.url')
check "返回 /uploads/ 访问地址" "true" "$(echo "$UPLOAD_URL" | grep -q '^/uploads/' && echo true || echo false)"
check "上传后可通过静态地址访问" "200" "$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' "$SERVER$UPLOAD_URL")"
rm -f "$TMP_PNG"

check "未登录上传被拒绝" "10001" "$(curl -s --noproxy '*' -X POST "$BASE/files/upload" \
  -F "file=@/dev/null;filename=empty.png" | jq -r '.code')"

echo
if [ "$FAIL" -eq 0 ]; then
  green "全部通过：$PASS 项"
  exit 0
else
  red "通过 $PASS 项，失败 $FAIL 项"
  exit 1
fi
