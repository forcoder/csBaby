#!/bin/bash
# csBaby 全功能综合测试

PASS=0
FAIL=0

check() {
    local name="$1"
    local code="$2"
    local expect_ok="$3"
    if [ "$expect_ok" = "true" ] && [ "$code" = "200" ]; then
        echo "  [PASS] $name (HTTP $code)"
        PASS=$((PASS+1))
    elif [ "$expect_ok" = "false" ] && [ "$code" != "200" ] && [ "$code" != "000" ] && [ "$code" != "TIMEOUT" ]; then
        echo "  [PASS] $name (HTTP $code - expected non-200)"
        PASS=$((PASS+1))
    elif [ "$expect_ok" = "any" ]; then
        echo "  [INFO] $name (HTTP $code)"
        PASS=$((PASS+1))
    else
        echo "  [FAIL] $name (HTTP $code, expected ok=$expect_ok)"
        FAIL=$((FAIL+1))
    fi
}

api_test() {
    local name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local expect_ok="$5"
    
    if [ "$method" = "POST" ]; then
        code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 -X POST \
            -H "Content-Type: application/json" -d "$data" "$url" 2>/dev/null || echo "TIMEOUT")
    else
        code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$url" 2>/dev/null || echo "TIMEOUT")
    fi
    check "$name" "$code" "$expect_ok"
    [ "$FAIL" -gt 5 ] && { echo "太多失败, 终止测试"; exit 1; }
}

echo ""
echo "============================================"
echo " csBaby 全功能综合测试"
echo "============================================"
echo ""

# ========== 1. api.agentai0.com ==========
echo "--- 1. api.agentai0.com 路由连通性 ---"
api_test "根路由 /" GET "http://api.agentai0.com/" "" "any"
api_test "健康检查 /health" GET "http://api.agentai0.com/health" "" "true"

echo "--- 1b. 认证路由 (→ csbaby-sync) ---"
api_test "/auth/register (空)" POST "http://api.agentai0.com/auth/register" '{}' "false"
api_test "/auth/register (正确)" POST "http://api.agentai0.com/auth/register" '{"email":"fulltest@test.com","password":"test123","displayName":"Full Test"}' "true"
api_test "/auth/login (正确)" POST "http://api.agentai0.com/auth/login" '{"email":"fulltest@test.com","password":"test123"}' "true"
api_test "/auth/login (错误)" POST "http://api.agentai0.com/auth/login" '{"email":"fulltest@test.com","password":"wrong"}' "false"
api_test "/auth/refresh (空)" POST "http://api.agentai0.com/auth/refresh" '{}' "false"

echo "--- 1c. 同步路由 (→ csbaby-sync) ---"
api_test "/sync/push (无auth)" POST "http://api.agentai0.com/sync/push" '{}' "false"
api_test "/sync/all (无auth)" GET "http://api.agentai0.com/sync/all" "" "false"
api_test "/sync/changes (无auth)" GET "http://api.agentai0.com/sync/changes?since=0" "" "false"
api_test "/sync (无auth)" GET "http://api.agentai0.com/sync" "" "false"
api_test "/sync/resolve (无auth)" POST "http://api.agentai0.com/sync/resolve" '{}' "false"

echo "--- 1d. 主 API 业务路由 (→ csbaby-api) ---"
api_test "/api/auth/user/login" POST "http://api.agentai0.com/api/auth/user/login" '{"identifier":"fulltest@test.com","password":"test123"}' "true"
api_test "/api/auth/user/register" POST "http://api.agentai0.com/api/auth/user/register" '{"email":"apitest@test.com","password":"test123"}' "any"
api_test "/api/rules" GET "http://api.agentai0.com/api/rules?tenantId=test" "" "any"
api_test "/api/models" GET "http://api.agentai0.com/api/models?tenantId=test" "" "any"

echo "--- 1e. 错误路径 ---"
api_test "404路径 /nonexistent" GET "http://api.agentai0.com/nonexistent" "" "false"

# ========== 2. sync.agentai0.com ==========
echo ""
echo "--- 2. sync.agentai0.com ---"
api_test "根路由 /" GET "http://sync.agentai0.com/" "" "true"
api_test "健康检查 /health" GET "http://sync.agentai0.com/health" "" "true"
api_test "/auth/register" POST "http://sync.agentai0.com/auth/register" '{"email":"synctest@test.com","password":"test123","displayName":"Sync Test"}' "true"
api_test "/auth/login" POST "http://sync.agentai0.com/auth/login" '{"email":"synctest@test.com","password":"test123"}' "true"
api_test "/sync/push (无auth)" POST "http://sync.agentai0.com/sync/push" '{}' "false"

# ========== 3. admin.agentai0.com ==========
echo ""
echo "--- 3. admin.agentai0.com ---"
api_test "/login" GET "http://admin.agentai0.com/login" "" "any"
api_test "/setup" GET "http://admin.agentai0.com/setup" "" "any"

# ========== 4. 压力测试 ==========
echo ""
echo "--- 4. 连接池压力测试 (10个连续register) ---"
for i in $(seq 1 10); do
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 -X POST \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"stress${i}@test.com\",\"password\":\"test123\",\"displayName\":\"Stress ${i}\"}" \
        "http://api.agentai0.com/auth/register" 2>/dev/null || echo "TIMEOUT")
    check "压力测试 #$i" "$code" "true"
done

# ========== 5. 混合测试 ==========
echo ""
echo "--- 5. 认证混合测试 ---"
for i in $(seq 1 5); do
    code1=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 -X POST \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"mix${i}a@test.com\",\"password\":\"test123\",\"displayName\":\"Mix ${i}A\"}" \
        "http://api.agentai0.com/auth/register" 2>/dev/null || echo "TIMEOUT")
    check "混合 register #${i}" "$code1" "true"
    
    code2=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 -X POST \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"mix${i}a@test.com\",\"password\":\"test123\"}" \
        "http://api.agentai0.com/auth/login" 2>/dev/null || echo "TIMEOUT")
    check "混合 login #${i}" "$code2" "true"
done

# ========== 6. 间歇后首次 ==========
echo ""
echo "--- 6. 间歇后首次请求 ---"
sleep 5
api_test "空闲5秒后 /auth/login" POST "http://api.agentai0.com/auth/login" \
    '{"email":"fulltest@test.com","password":"test123"}' "true"

# ========== 结果 ==========
echo ""
echo "============================================"
if [ "$FAIL" -eq 0 ]; then
    echo " 全部 ${PASS} 项测试通过 ✅"
else
    echo " ${PASS} 通过, ${FAIL} 失败 ❌"
fi
echo "============================================"
