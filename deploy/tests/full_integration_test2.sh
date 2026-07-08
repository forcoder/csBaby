#!/bin/bash
# csBaby 全功能回归测试
PASS=0; FAIL=0
TS=$(date +%s)

check() {
    local name="$1" code="$2" expect_ok="$3"
    if [ "$expect_ok" = "true" ] && [ "$code" = "200" ]; then
        echo "  [PASS] $name (HTTP $code)"; PASS=$((PASS+1))
    elif [ "$expect_ok" = "false" ] && [ "$code" != "200" ] && [ "$code" != "000" ] && [ "$code" != "TIMEOUT" ]; then
        echo "  [PASS] $name (HTTP $code)" ; PASS=$((PASS+1))
    elif [ "$expect_ok" = "any" ]; then
        echo "  [INFO] $name (HTTP $code)" ; PASS=$((PASS+1))
    else
        echo "  [FAIL] $name (HTTP $code, expected=$expect_ok)"; FAIL=$((FAIL+1))
    fi
}

api() {
    local name="$1" method="$2" url="$3" data="$4" expect_ok="$5"
    if [ "$method" = "POST" ]; then
        code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 -X POST \
            -H "Content-Type: application/json" -d "$data" "$url" 2>/dev/null || echo "TIMEOUT")
    else
        code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$url" 2>/dev/null || echo "TIMEOUT")
    fi
    check "$name" "$code" "$expect_ok"
}

echo ""
echo "============================================"
echo " csBaby 全功能回归测试"
echo "============================================"
echo ""

echo "--- 1. api.agentai0.com 基础路由 ---"
api "GET /"           GET  "http://api.agentai0.com/"               "" "any"
api "GET /health"     GET  "http://api.agentai0.com/health"         "" "true"
api "GET /nonexistent" GET "http://api.agentai0.com/nonexistent"    "" "false"

echo ""
echo "--- 2. 认证路由 (api → csbaby-sync) ---"
api "/auth (bare)"    GET  "http://api.agentai0.com/auth"           "" "false"
api "/auth/register (空)" POST "http://api.agentai0.com/auth/register" '{}' "false"
api "/auth/register (new)" POST "http://api.agentai0.com/auth/register" \
    "{\"email\":\"regtest${TS}a@test.com\",\"password\":\"test123\",\"displayName\":\"Reg ${TS}A\"}" "true"
api "/auth/login (正确)" POST "http://api.agentai0.com/auth/login" \
    "{\"email\":\"regtest${TS}a@test.com\",\"password\":\"test123\"}" "true"
api "/auth/login (错误)" POST "http://api.agentai0.com/auth/login" \
    "{\"email\":\"regtest${TS}a@test.com\",\"password\":\"wrong\"}" "false"
api "/auth/refresh (空)" POST "http://api.agentai0.com/auth/refresh" '{}' "false"

echo ""
echo "--- 3. 同步路由 (api → csbaby-sync) ---"
api "/sync (bare)"    GET  "http://api.agentai0.com/sync"               "" "false"
api "/sync/push"      POST "http://api.agentai0.com/sync/push"          '{}' "false"
api "/sync/all"       GET  "http://api.agentai0.com/sync/all"            "" "false"
api "/sync/changes"   GET  "http://api.agentai0.com/sync/changes?since=0" "" "false"
api "/sync/resolve"   POST "http://api.agentai0.com/sync/resolve"       '{}' "false"

echo ""
echo "--- 4. 主 API 业务路由 (api → csbaby-api) ---"
api "/api/auth/user/login" POST "http://api.agentai0.com/api/auth/user/login" \
    '{"identifier":"newuser@main.com","password":"test123"}' "false"
api "/api/auth/user/register" POST "http://api.agentai0.com/api/auth/user/register" \
    "{\"email\":\"mainapi${TS}@test.com\",\"password\":\"test123\"}" "any"
api "/api/rules"      GET  "http://api.agentai0.com/api/rules"        "" "any"
api "/api/models"     GET  "http://api.agentai0.com/api/models"       "" "any"

echo ""
echo "--- 5. sync.agentai0.com 独立域名 ---"
api "GET /"           GET  "http://sync.agentai0.com/"                "" "true"
api "GET /health"     GET  "http://sync.agentai0.com/health"          "" "true"
api "/auth/register (new)" POST "http://sync.agentai0.com/auth/register" \
    "{\"email\":\"synctest${TS}@test.com\",\"password\":\"test123\",\"displayName\":\"Sync ${TS}\"}" "true"
api "/auth/login"     POST "http://sync.agentai0.com/auth/login" \
    "{\"email\":\"synctest${TS}@test.com\",\"password\":\"test123\"}" "true"
api "/sync/push (无auth)" POST "http://sync.agentai0.com/sync/push" '{}' "false"

echo ""
echo "--- 6. admin.agentai0.com ---"
api "GET /login"      GET  "http://admin.agentai0.com/login"          "" "any"
api "GET /setup"      GET  "http://admin.agentai0.com/setup"          "" "any"

echo ""
echo "--- 7. 连接池压力测试 (10个连续register) ---"
for i in $(seq 1 10); do
    api "压力测试 #$i" POST "http://api.agentai0.com/auth/register" \
        "{\"email\":\"stress${TS}_${i}@test.com\",\"password\":\"test123\",\"displayName\":\"Stress ${TS}_${i}\"}" "true"
done

echo ""
echo "--- 8. 认证混合测试 ---"
for i in $(seq 1 5); do
    api "混合 register #$i" POST "http://api.agentai0.com/auth/register" \
        "{\"email\":\"mix${TS}_${i}@test.com\",\"password\":\"test123\",\"displayName\":\"Mix ${TS}_${i}\"}" "true"
    api "混合 login #$i" POST "http://api.agentai0.com/auth/login" \
        "{\"email\":\"mix${TS}_${i}@test.com\",\"password\":\"test123\"}" "true"
done

echo ""
echo "--- 9. 空闲后首次请求 ---"
sleep 5
api "空闲5秒后 /auth/login" POST "http://api.agentai0.com/auth/login" \
    "{\"email\":\"regtest${TS}a@test.com\",\"password\":\"test123\"}" "true"

echo ""
echo "--- 10. Nginx 边缘路径 ---"
api "GET /auth"       GET  "http://api.agentai0.com/auth"               "" "false"
api "GET /sync"       GET  "http://api.agentai0.com/sync"               "" "false"
api "GET /api/v1/backup" GET "http://api.agentai0.com/api/v1/backup"    "" "false"
api "GET /api/v1/backup/list" GET "http://api.agentai0.com/api/v1/backup/list" "" "false"

echo ""
echo "============================================"
if [ "$FAIL" -eq 0 ]; then
    echo " 全部 ${PASS} 项测试通过 ✅"
else
    echo " ${PASS} 通过, ${FAIL} 失败 ❌"
fi
echo "============================================"
