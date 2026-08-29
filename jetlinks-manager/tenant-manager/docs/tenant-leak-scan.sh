#!/usr/bin/env bash
# ============================================================
# 租户越权扫描基线
#
# 用租户A的身份遍历查询接口，断言看不到租户B的数据。
#
# 关键设计：把「权限不足(403)」与「隔离生效(0条)」严格区分。
#   早期版本没区分，把 403 当成隔离成功，导致隔离完全失效却被判为通过。
#
# 用法:
#   ./tenant-leak-scan.sh <BASE_URL> <USER> <PASS> <FOREIGN_ID> <MY_TENANT_ID>
# 例:
#   ./tenant-leak-scan.sh http://127.0.0.1:8858 tenant-a Tenant@2026 2093707859930353664 t001
#
# FOREIGN_ID: 一条不属于租户A的设备ID，用于 findById 直读越权测试。
# ============================================================
set -u

BASE_URL="${1:?usage: $0 <base-url> <user> <pass> <foreign-device-id> <my-tenant-id>}"
USER="${2:?missing user}"
PASS="${3:?missing pass}"
FOREIGN_ID="${4:?missing foreign device id}"
MY_TENANT="${5:?missing my tenant id}"   # 本账号所属租户，用于判定返回数据归属

PASS_N=0; LEAK_N=0; DENY_N=0; SKIP_N=0

hdr() { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok()   { printf '  \033[32m[PASS]\033[0m %s\n' "$1"; PASS_N=$((PASS_N+1)); }
leak() { printf '  \033[31m[LEAK]\033[0m %s\n' "$1"; LEAK_N=$((LEAK_N+1)); }
deny() { printf '  \033[33m[DENY]\033[0m %s (403 权限不足，非隔离结果，无法判定)\n' "$1"; DENY_N=$((DENY_N+1)); }

TOKEN=$(curl -s -X POST "$BASE_URL/authorize/login" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
        | grep -oE '"token":"[^"]+"' | head -1 | cut -d'"' -f4)
[ -z "$TOKEN" ] && { echo "登录失败，无法扫描"; exit 2; }
echo "已以 $USER 身份登录"

# ---------- 1. findById 直读越权（最典型的越权路径） ----------
hdr "1. findById 直读他人数据"
BODY=$(curl -s -o /dev/null -w '%{http_code}' -H "X-Access-Token: $TOKEN" \
       "$BASE_URL/device-instance/$FOREIGN_ID")
FULL=$(curl -s -H "X-Access-Token: $TOKEN" "$BASE_URL/device-instance/$FOREIGN_ID")
if [ "$BODY" = "403" ]; then
    deny "GET /device-instance/{foreignId}"
elif echo "$FULL" | grep -q '"result"[[:space:]]*:[[:space:]]*{'; then
    leak "GET /device-instance/{foreignId} 拿到了他人设备"
else
    ok "GET /device-instance/{foreignId} 无数据"
fi

# ---------- 2. 列表查询 ----------
hdr "2. 列表查询是否混入他人数据"
for path in \
    "/device-instance/_query" \
    "/device-product/_query" \
    "/rule-engine/instance/_query" \
    "/scene/_query" \
    "/alarm/config/_query" \
    "/notify/config/_query" \
    "/file/_query" \
    "/organization/_query" \
    "/role/_query"
do
    RESP=$(curl -s -X POST "$BASE_URL$path" -H "X-Access-Token: $TOKEN" \
           -H 'Content-Type: application/json' -d '{"paging":false}')
    CODE=$(echo "$RESP" | grep -oE '"status":[0-9]+' | head -1 | cut -d: -f2)
    if [ "$CODE" = "403" ]; then deny "POST $path"; continue; fi
    if echo "$RESP" | grep -q "$FOREIGN_ID"; then
        leak "POST $path 返回中含他人数据 $FOREIGN_ID"
    else
        # 校验返回数据的租户归属：必须全部等于本租户。
        # 早期版本只判断「是否出现多个租户」，导致 tenant-a 只看到 default
        # 一个租户的数据时被误判为 PASS —— 那恰恰是越权。
        SEEN=$(echo "$RESP" | grep -oE '"tenantId":"[^"]*"' | cut -d'"' -f4 | sort -u | grep -v '^$')
        if [ -z "$SEEN" ]; then
            ROWS=$(echo "$RESP" | grep -oE '"id":"' | wc -l)
            if [ "$ROWS" -gt 0 ]; then
                leak "POST $path 返回 $ROWS 条但无 tenantId 字段(该实体未纳入隔离)"
            else
                ok "POST $path 无数据"
            fi
        else
            FOREIGN=$(echo "$SEEN" | grep -v "^${MY_TENANT}$" || true)
            if [ -n "$FOREIGN" ]; then
                leak "POST $path 含他租户数据: $(echo $FOREIGN | tr "\n" " ") (本租户应为 $MY_TENANT)"
            else
                ok "POST $path 仅含本租户 $MY_TENANT"
            fi
        fi
    fi
done

# ---------- 3. 平台专属接口应被拒绝 ----------
hdr "3. 平台专属接口对租户用户应 403"
for path in "/tenant/_query" "/tenant/plan/_query" "/tenant/order/_query" "/tenant/invoice/_query"; do
    CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL$path" \
           -H "X-Access-Token: $TOKEN" -H 'Content-Type: application/json' -d '{"paging":false}')
    if [ "$CODE" = "403" ]; then
        ok "POST $path 已拒绝 (403)"
    else
        leak "POST $path 未拒绝 (HTTP $CODE) —— 租户可访问平台接口"
    fi
done

# ---------- 汇总 ----------
printf '\n============================================\n'
printf '通过 %d   越权 %d   无法判定(403) %d\n' "$PASS_N" "$LEAK_N" "$DENY_N"
if [ "$DENY_N" -gt 0 ]; then
    printf '注意: DENY 项因权限不足未能验证隔离，需给测试账号补权限后重跑。\n'
fi
printf '============================================\n'
[ "$LEAK_N" -eq 0 ]
