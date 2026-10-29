#!/usr/bin/env bash
# 09-security 验收脚本
#
# 用法：先在仓库根目录起后端
#   mvn -pl 09-security spring-boot:run
# 再执行
#   ./check.sh        # 跑全部 24 条
#   ./check.sh 1      # 只跑关卡 1（认证）
#   ./check.sh 2 3    # 跑关卡 2 和 3
#
# 后端不在 8009 时用 BASE 覆盖：
#   BASE=http://127.0.0.1:8010 ./check.sh

set -uo pipefail

BASE="${BASE:-http://127.0.0.1:8009}"
STAGE="${*:-all}"
TMP="$(mktemp -t secbody)"
trap 'rm -f "$TMP"' EXIT

PASS=0
FAIL=0

green() { printf '\033[32m%s\033[0m' "$1"; }
red()   { printf '\033[31m%s\033[0m' "$1"; }
dim()   { printf '\033[2m%s\033[0m' "$1"; }

pass() { PASS=$((PASS + 1)); printf '  %s  %s\n' "$(green PASS)" "$1"; }
fail() { FAIL=$((FAIL + 1)); printf '  %s  %s\n        %s\n' "$(red FAIL)" "$1" "$(dim "${2:-}")"; }

# 发请求，$@ 原样交给 curl，回显 HTTP 状态码，响应体落到 $TMP
call() {
  curl -s -o "$TMP" -w '%{http_code}' --noproxy '*' "$@" 2>/dev/null || echo 000
}

# 取上次响应里的字段，路径用点号分隔，如 data.token、data.0.owner
field() {
  python3 - "$1" "$TMP" <<'PY'
import json, sys
path, f = sys.argv[1], sys.argv[2]
try:
    d = json.load(open(f))
except Exception:
    print('')
    raise SystemExit
for p in [x for x in path.split('.') if x]:
    if isinstance(d, list):
        try:
            d = d[int(p)]
        except (IndexError, ValueError):
            print('')
            raise SystemExit
    elif isinstance(d, dict) and p in d:
        d = d[p]
    else:
        print('')
        raise SystemExit
    if d is None:
        print('')
        raise SystemExit
if isinstance(d, (dict, list)):
    print(json.dumps(d, ensure_ascii=False))
else:
    print(d)
PY
}

# 字段是否存在，输出 yes 或 no
has() {
  python3 - "$1" "$TMP" <<'PY'
import json, sys
path, f = sys.argv[1], sys.argv[2]
try:
    d = json.load(open(f))
except Exception:
    print('no')
    raise SystemExit
for p in [x for x in path.split('.') if x]:
    if isinstance(d, list):
        d = d[int(p)]
    elif isinstance(d, dict) and p in d:
        d = d[p]
    else:
        print('no')
        raise SystemExit
print('yes')
PY
}

# 数组里每一条的 owner 是否都是 $1，输出 yes 或 no
all_owner() {
  python3 - "$1" "$TMP" <<'PY'
import json, sys
who, f = sys.argv[1], sys.argv[2]
try:
    rows = json.load(open(f))['data']
except Exception:
    print('no')
    raise SystemExit
if not isinstance(rows, list):
    print('no')
    raise SystemExit
print('yes' if all(r.get('owner') == who for r in rows) else 'no')
PY
}

# 登录换令牌，失败回显空串
login() {
  call -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" >/dev/null
  field data.token
}

submit() {
  call -X POST "$BASE/api/tasks" -H "Authorization: Bearer $1" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"$2\",\"content\":\"按实训要求做完了，代码见附件。\"}" >/dev/null
  field data.id
}

echo "后端 $BASE"

if ! curl -s -o /dev/null --noproxy '*' --max-time 3 "$BASE/public/ping" 2>/dev/null; then
  echo "连不上后端。先在仓库根目录执行 mvn -pl 09-security spring-boot:run"
  exit 1
fi

want() { [ "$STAGE" = all ] || printf '%s\n' "$STAGE" | tr ' ' '\n' | grep -qx "$1"; }

# ==================== 关卡 1 · 认证 ====================
if want 1; then
  echo
  echo "关卡 1 · 认证"

  code=$(call "$BASE/public/ping")
  if [ "$code" = 200 ] && [ "$(field data.status)" = ok ]; then
    pass "01 放行接口不带令牌也返回 200"
  else
    fail "01 放行接口不带令牌也返回 200" "状态码 $code，data.status=$(field data.status)"
  fi

  code=$(call "$BASE/api/tasks/mine")
  if [ "$code" = 401 ] && [ "$(field code)" = 401 ]; then
    pass "02 无令牌访问 /api/tasks/mine 返回 401"
  else
    fail "02 无令牌访问 /api/tasks/mine 返回 401" "状态码 $code，code=$(field code)"
  fi

  code=$(call "$BASE/api/tasks")
  if [ "$code" = 401 ]; then
    pass "03 无令牌访问 /api/tasks 返回 401"
  else
    fail "03 无令牌访问 /api/tasks 返回 401" "状态码 $code"
  fi

  code=$(call -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"student01","password":"Student#2026"}')
  token=$(field data.token)
  if [ "$code" = 200 ] && [ -n "$token" ] && [ "$(field data.tokenType)" = Bearer ] \
    && [ -n "$(field data.expiresAt)" ] && field data.roles | grep -q STUDENT; then
    pass "04 登录成功返回令牌、类型、过期时间与角色"
  else
    fail "04 登录成功返回令牌、类型、过期时间与角色" \
      "状态码 $code，token 为空=$([ -z "$token" ] && echo 是 || echo 否)，roles=$(field data.roles)"
  fi

  call -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"student01","password":"wrong-password"}' >/dev/null
  if [ "$(field code)" = 30001 ]; then
    pass "05 密码错误返回 30001"
  else
    fail "05 密码错误返回 30001" "code=$(field code)"
  fi

  call -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"nobody","password":"whatever"}' >/dev/null
  if [ "$(field code)" = 30001 ]; then
    pass "06 用户不存在也返回 30001"
  else
    fail "06 用户不存在也返回 30001" "code=$(field code)，两种失败不该给不同提示"
  fi

  call -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"guest","password":"Guest#2026"}' >/dev/null
  if [ "$(field code)" = 30002 ]; then
    pass "07 停用账号登录返回 30002"
  else
    fail "07 停用账号登录返回 30002" "code=$(field code)"
  fi

  call -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"student01"}' >/dev/null
  if [ "$(field code)" = 400 ] && [ "$(field message)" = 密码不能为空 ]; then
    pass "08 缺密码返回 400 与校验提示"
  else
    fail "08 缺密码返回 400 与校验提示" "code=$(field code)，message=$(field message)"
  fi

  if [ -z "$token" ]; then
    token=$(login student01 'Student#2026')
  fi
  code=$(call "$BASE/auth/me" -H "Authorization: Bearer $token")
  if [ "$code" = 200 ] && [ "$(field data.username)" = student01 ] && [ "$(has data.password)" = no ]; then
    pass "09 带令牌换当前用户，响应里没有密码"
  else
    fail "09 带令牌换当前用户，响应里没有密码" \
      "状态码 $code，username=$(field data.username)，含 password=$(has data.password)"
  fi

  bad_token="${token%???}abc"
  code=$(call "$BASE/auth/me" -H "Authorization: Bearer $bad_token")
  if [ "$code" = 401 ] && [ "$(field code)" = 401 ]; then
    pass "10 令牌被改过就按未认证处理"
  else
    fail "10 令牌被改过就按未认证处理" "状态码 $code，说明只解码没验签"
  fi
fi

# ==================== 关卡 2 · 授权 ====================
if want 2; then
  echo
  echo "关卡 2 · 授权"

  student=$(login student01 'Student#2026')
  teacher=$(login teacher01 'Teacher#2026')
  admin=$(login admin 'Admin#2026')

  code=$(call "$BASE/api/tasks" -H "Authorization: Bearer $student")
  if [ "$code" = 403 ]; then
    pass "11 学生看全部提交返回 403"
  else
    fail "11 学生看全部提交返回 403" "状态码 $code，检查 @EnableMethodSecurity 有没有加"
  fi

  code=$(call -X POST "$BASE/api/tasks/2/review" -H "Authorization: Bearer $student" \
    -H 'Content-Type: application/json' -d '{"score":100,"comment":"自己给自己打满分"}')
  if [ "$code" = 403 ]; then
    pass "12 学生调用批改接口返回 403"
  else
    fail "12 学生调用批改接口返回 403" "状态码 $code"
  fi

  code=$(call "$BASE/api/tasks" -H "Authorization: Bearer $teacher")
  if [ "$code" = 200 ] && [ "$(has data.0.owner)" = yes ]; then
    pass "13 教师看得到全部提交与提交人"
  else
    fail "13 教师看得到全部提交与提交人" "状态码 $code，data.0.owner=$(has data.0.owner)"
  fi

  http=$(call -X POST "$BASE/api/tasks" -H "Authorization: Bearer $teacher" \
    -H 'Content-Type: application/json' \
    -d '{"title":"教师不该能交任务","content":"这条应该被拦住"}')
  if [ "$http" = 403 ]; then
    pass "14 教师调用提交接口返回 403"
  else
    fail "14 教师调用提交接口返回 403" "状态码 $http，code=$(field code)"
  fi

  code=$(call "$BASE/admin/users" -H "Authorization: Bearer $teacher")
  if [ "$code" = 403 ]; then
    pass "15 教师进不了管理接口"
  else
    fail "15 教师进不了管理接口" "状态码 $code"
  fi

  code=$(call "$BASE/admin/users" -H "Authorization: Bearer $admin")
  if [ "$code" = 200 ] && [ "$(has data.0.username)" = yes ] && [ "$(has data.0.password)" = no ]; then
    pass "16 管理员看到账号列表，响应里没有密码"
  else
    fail "16 管理员看到账号列表，响应里没有密码" \
      "状态码 $code，含 password=$(has data.0.password)"
  fi
fi

# ==================== 关卡 3 · 归属 ====================
if want 3; then
  echo
  echo "关卡 3 · 归属"

  student=$(login student01 'Student#2026')
  teacher=$(login teacher01 'Teacher#2026')
  stamp=$(date +%H%M%S)

  new_id=$(submit "$student" "验收提交 $stamp")
  owner=$(field data.owner)
  if [ -n "$new_id" ] && [ "$owner" = student01 ] \
    && [ "$(field data.status)" = SUBMITTED ] && [ "$(field data.statusText)" = 待批改 ]; then
    pass "17 学生提交任务，归属由服务端写成当前登录人"
  else
    fail "17 学生提交任务，归属由服务端写成当前登录人" \
      "id=$new_id，owner=$owner，statusText=$(field data.statusText)"
  fi

  call -X POST "$BASE/api/tasks" -H "Authorization: Bearer $student" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"验收伪造归属 $stamp\",\"content\":\"试试改 owner\",\"owner\":\"student02\"}" >/dev/null
  if [ "$(field data.owner)" = student01 ]; then
    pass "18 请求体里塞别人的用户名不生效"
  else
    fail "18 请求体里塞别人的用户名不生效" "owner=$(field data.owner)"
  fi

  call "$BASE/api/tasks/mine" -H "Authorization: Bearer $student" >/dev/null
  if [ "$(all_owner student01)" = yes ]; then
    pass "19 我的提交里只有自己的"
  else
    fail "19 我的提交里只有自己的" "列表里混进了别人的 owner"
  fi

  code=$(call "$BASE/api/tasks/2" -H "Authorization: Bearer $student")
  if [ "$code" = 403 ] && [ "$(field code)" = 403 ]; then
    pass "20 学生看别人的任务返回真实 403"
  else
    fail "20 学生看别人的任务返回真实 403" "状态码 $code，code=$(field code)"
  fi

  code=$(call "$BASE/api/tasks/$new_id" -H "Authorization: Bearer $student")
  if [ "$code" = 200 ] && [ "$(field data.owner)" = student01 ]; then
    pass "21 学生能看自己那条的详情"
  else
    fail "21 学生能看自己那条的详情" "状态码 $code"
  fi

  code=$(call -X POST "$BASE/api/tasks/$new_id/review" -H "Authorization: Bearer $teacher" \
    -H 'Content-Type: application/json' \
    -d '{"score":88,"comment":"归属判断对了，注意异常分支"}')
  reviewed=$(field data.status)
  score=$(field data.score)

  call "$BASE/api/tasks/$new_id" -H "Authorization: Bearer $student" >/dev/null
  seen=$(field data.score)

  call -X POST "$BASE/api/tasks/$new_id/review" -H "Authorization: Bearer $teacher" \
    -H 'Content-Type: application/json' -d '{"score":60,"comment":"改个分"}' >/dev/null
  again=$(field code)

  if [ "$code" = 200 ] && [ "$reviewed" = REVIEWED ] && [ "$score" = 88 ] \
    && [ "$seen" = 88 ] && [ "$again" = 30012 ]; then
    pass "22 教师批改后学生看到分数，同一份不能批两次"
  else
    fail "22 教师批改后学生看到分数，同一份不能批两次" \
      "批改状态码 $code，status=$reviewed，score=$score，学生看到 $seen，重复批改 code=$again"
  fi
fi

# ==================== 关卡 4 · 账号管理 ====================
if want 4; then
  echo
  echo "关卡 4 · 账号管理"

  admin=$(login admin 'Admin#2026')

  if [ -n "$(login student99 'NewStu#2026')" ]; then
    echo "  跳过 23：student99 已存在，先清理再用"
  else
    call -X POST "$BASE/admin/users" -H "Authorization: Bearer $admin" \
      -H 'Content-Type: application/json' \
      -d '{"username":"student99","password":"NewStu#2026","nickname":"新加的同学","roles":["STUDENT"]}' >/dev/null
    created=$(has data.roles)

    new_token=$(login student99 'NewStu#2026')
    call "$BASE/api/tasks/mine" -H "Authorization: Bearer $new_token" >/dev/null
    empty=$(field data)

    if [ "$created" = yes ] && [ -n "$new_token" ] && [ "$empty" = "[]" ]; then
      pass "23 新增账号能登录，且我的提交为空"
    else
      fail "23 新增账号能登录，且我的提交为空" \
        "建账号返回含 roles=$created，能登录=$([ -n "$new_token" ] && echo 是 || echo 否)，mine=$empty"
    fi
  fi

  call -X DELETE "$BASE/admin/users/admin" -H "Authorization: Bearer $admin" >/dev/null
  if [ "$(field code)" = 30005 ]; then
    pass "24 不能删掉当前登录的账号"
  else
    fail "24 不能删掉当前登录的账号" "code=$(field code)"
  fi
fi

echo
echo "通过 $PASS 条，失败 $FAIL 条"
[ "$FAIL" = 0 ] || exit 1
