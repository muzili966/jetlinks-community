#!/usr/bin/env bash
# ============================================================
# 越权扫描基线(§7): 用租户A的token遍历所有GET查询接口,
# 断言返回中不含租户B的标记数据.
#
# 用法:
#   1. 分别以租户A用户、租户B用户登录获取token
#   2. 租户B先创建带标记的数据(设备名/产品名含 LEAK_MARKER)
#   3. ./tenant-leak-scan.sh <BASE_URL> <TENANT_A_TOKEN> [MARKER]
#
# 改造前跑一遍应几乎全红; 每完成一个模块跑一遍, 红色数字即剩余工作量.
# ============================================================
set -u

BASE_URL="${1:?usage: $0 <base-url> <tenant-a-token> [marker]}"
TOKEN="${2:?missing tenant-a token}"
MARKER="${3:-TENANT_B_MARKER}"

PASS=0; LEAK=0; SKIP=0

# 从 springdoc 拿全部 GET 接口
paths=$(curl -sf -H "X-Access-Token: $TOKEN" "$BASE_URL/v3/api-docs" \
        | grep -o '"/[^"]*"[[:space:]]*:[[:space:]]*{[^}]*"get"' \
        | grep -o '"/[^"]*"' | tr -d '"' | sort -u)

if [ -z "$paths" ]; then
    echo "无法获取 api-docs, 检查服务地址与token"
    exit 2
fi

for path in $paths; do
    # 跳过带路径参数的接口(需要具体ID, 单独用例覆盖)
    case "$path" in
        *\{*) SKIP=$((SKIP+1)); continue ;;
    esac
    body=$(curl -s -m 10 -H "X-Access-Token: $TOKEN" "$BASE_URL$path")
    if printf '%s' "$body" | grep -q "$MARKER"; then
        echo "[LEAK] $path"
        LEAK=$((LEAK+1))
    else
        PASS=$((PASS+1))
    fi
done

echo "------------------------------------------"
echo "pass=$PASS leak=$LEAK skip(path-param)=$SKIP"
[ "$LEAK" -eq 0 ]
