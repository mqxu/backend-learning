#!/usr/bin/env bash
# 端到端冒烟测试：登录 -> 建活动 -> 审核 -> 报名 -> 满员候补 -> 签到 -> 统计
# 用法：./scripts/smoke.sh
# 依赖：后端已启动在 127.0.0.1:8080，本机有 curl 与 jq

set -uo pipefail

BASE=${BASE:-http://127.0.0.1:8080/api}
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

echo
if [ "$FAIL" -eq 0 ]; then
  green "全部通过：$PASS 项"
  exit 0
else
  red "通过 $PASS 项，失败 $FAIL 项"
  exit 1
fi
