"""Tests for Nginx proxy routing (Phase: Apache → Nginx 迁移).

场景:
  之前 api.agentai0.com 通过 WordPress 容器内的 Apache vhost 做反向代理,
  所有请求都转发到主 API 服务器 (csbaby-api:8080), 导致 /auth/* 和 /sync/*
  路由 404。

当前架构:
  主机 Nginx (端口 80/443) 直接做反向代理, csbaby 容器映射到主机端口:
    csbaby-api:8080  → host:8084
    csbaby-sync:8080 → host:8085
    csbaby-admin:8080 → host:8086

  Nginx 路由分发:
    api.agentai0.com/auth/*  → 127.0.0.1:8085 (csbaby-sync)
    api.agentai0.com/sync/*  → 127.0.0.1:8085 (csbaby-sync)
    api.agentai0.com/*       → 127.0.0.1:8084 (csbaby-api)
    sync.agentai0.com/*      → 127.0.0.1:8085 (csbaby-sync)
    admin.agentai0.com/*     → 127.0.0.1:8086 (csbaby-admin)
"""
import os
import re

# 路径配置
NGINX_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "nginx")


# ========== Nginx 配置解析辅助 ==========

def parse_nginx_location_rules(conf_path):
    """解析 Nginx location 块, 返回 [(path, backend)]."""
    if not os.path.exists(conf_path):
        raise FileNotFoundError(f"Nginx config not found: {conf_path}")

    with open(conf_path, encoding="utf-8") as f:
        content = f.read()

    rules = []
    # 匹配 location /xxx/ { ... proxy_pass http://... }
    pattern = re.compile(
        r'location\s+(\S+)\s*\{.*?proxy_pass\s+(http://\S+?);',
        re.DOTALL
    )
    for m in pattern.finditer(content):
        path = m.group(1)
        backend = m.group(2)
        rules.append((path, backend))

    # 找 server_name
    server_name = ""
    sn_m = re.search(r'server_name\s+(\S+);', content)
    if sn_m:
        server_name = sn_m.group(1)

    return server_name, rules


def extract_flask_routes(app_path, extra_sys_path=None, env_overrides=None):
    """导入 Flask app 并提取所有路由."""
    import sys
    env = dict(os.environ)
    if env_overrides:
        os.environ.update(env_overrides)
    orig_path = list(sys.path)
    try:
        if extra_sys_path:
            sys.path.insert(0, extra_sys_path)
        module_name = f"_proxy_test_{abs(hash(app_path))}"
        import importlib.util
        spec = importlib.util.spec_from_file_location(module_name, app_path)
        if spec is None or spec.loader is None:
            return {}
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        app = getattr(mod, "app", None)
        if app is None:
            return {}
        rules = {}
        for rule in app.url_map.iter_rules():
            methods = set(rule.methods) - {"HEAD", "OPTIONS"}
            rules[rule.rule] = methods
        return rules
    except Exception as e:
        print(f"  [SKIP] Cannot load {app_path}: {type(e).__name__}: {e}")
        return {"__error__": str(e)}
    finally:
        sys.path = orig_path
        os.environ.clear()
        os.environ.update(env)


# ========== 测试 1: Nginx 配置文件完整性 ==========

def test_nginx_config_files_exist():
    """三份 Nginx 配置文件必须存在."""
    files = [
        "api.agentai0.com.conf",
        "sync.agentai0.com.conf",
        "admin.agentai0.com.conf",
    ]
    for f in files:
        path = os.path.join(NGINX_DIR, f)
        assert os.path.exists(path), f"Missing Nginx config: {path}"
        print(f"  [OK] {f}")


def test_nginx_config_server_names():
    """每份配置的 server_name 必须正确."""
    cases = [
        ("api.agentai0.com.conf", "api.agentai0.com"),
        ("sync.agentai0.com.conf", "sync.agentai0.com"),
        ("admin.agentai0.com.conf", "admin.agentai0.com"),
    ]
    for filename, expected_name in cases:
        server_name, _ = parse_nginx_location_rules(
            os.path.join(NGINX_DIR, filename)
        )
        assert server_name == expected_name, \
            f"{filename}: 期望 server_name={expected_name}, 实际={server_name}"
        print(f"  [OK] {filename} → server_name {server_name}")


# ========== 测试 2: api.agentai0.com 路由分发 ==========

def test_api_agentai0_routing_order():
    """api.agentai0.com 的 location 优先级: /auth/ > /sync/ > /api/v1/backup/ > /."""
    _, rules = parse_nginx_location_rules(
        os.path.join(NGINX_DIR, "api.agentai0.com.conf")
    )
    paths = [r[0] for r in rules]

    assert "/auth/" in paths, "缺少 /auth/ location"
    assert "/sync/" in paths, "缺少 /sync/ location"
    assert "/" in paths, "缺少 / (catch-all) location"

    # 顺序: 精确路径应在 / 之前
    auth_idx = paths.index("/auth/")
    sync_idx = paths.index("/sync/")
    catchall_idx = paths.index("/")

    assert auth_idx < catchall_idx, "/auth/ 必须在 / 之前"
    assert sync_idx < catchall_idx, "/sync/ 必须在 / 之前"
    print(f"  [OK] 路由顺序: /auth/(#{auth_idx}) → /sync/(#{sync_idx}) → /(#catchall_idx)")


def test_api_agentai0_backend_correct():
    """api.agentai0.com 的 proxy_pass 后端地址必须正确."""
    _, rules = parse_nginx_location_rules(
        os.path.join(NGINX_DIR, "api.agentai0.com.conf")
    )
    rules_dict = dict(rules)

    # /auth/ 和 /sync/ 必须到 csbaby-sync (port 8085)
    assert "127.0.0.1:8085" in rules_dict.get("/auth/", ""), \
        f"/auth/ 应代理到 8085, 实际: {rules_dict.get('/auth/', 'N/A')}"
    assert "127.0.0.1:8085" in rules_dict.get("/sync/", ""), \
        f"/sync/ 应代理到 8085, 实际: {rules_dict.get('/sync/', 'N/A')}"
    assert "127.0.0.1:8085" in rules_dict.get("/api/v1/backup/", ""), \
        f"/api/v1/backup/ 应代理到 8085, 实际: {rules_dict.get('/api/v1/backup/', 'N/A')}"

    # / 必须到 csbaby-api (port 8084)
    assert "127.0.0.1:8084" in rules_dict.get("/", ""), \
        f"/ 应代理到 8084, 实际: {rules_dict.get('/', 'N/A')}"

    print("  [OK] 后端地址检查通过:")
    for path, backend in rules:
        print(f"    {path:25s} → {backend}")


# ========== 测试 3: sync + admin 配置 ==========

def test_sync_admin_config():
    """sync.agentai0.com 和 admin.agentai0.com 配置必须正确."""
    for filename, expected_backend in [
        ("sync.agentai0.com.conf", "127.0.0.1:8085"),
        ("admin.agentai0.com.conf", "127.0.0.1:8086"),
    ]:
        server_name, rules = parse_nginx_location_rules(
            os.path.join(NGINX_DIR, filename)
        )
        # 有且只有一个 location /
        assert len(rules) == 1, f"{filename}: 应有1个 location, 实际 {len(rules)}"
        path, backend = rules[0]
        assert path == "/", f"{filename}: 应为 /, 实际 {path}"
        assert expected_backend in backend, \
            f"{filename}: 应代理到 {expected_backend}, 实际 {backend}"
        print(f"  [OK] {server_name:30s} → {backend}")


# ========== 测试 4: 模拟请求分发 ==========

def test_simulate_proxy_dispatch():
    """模拟请求通过 Nginx location 匹配后的分发路径."""
    test_cases = [
        # (domain, path, expected_backend_port)
        ("api.agentai0.com", "/auth/login",          8085),
        ("api.agentai0.com", "/auth/register",       8085),
        ("api.agentai0.com", "/auth/refresh",        8085),
        ("api.agentai0.com", "/sync/push",           8085),
        ("api.agentai0.com", "/sync/all",            8085),
        ("api.agentai0.com", "/sync/changes",        8085),
        ("api.agentai0.com", "/sync/resolve",        8085),
        ("api.agentai0.com", "/api/v1/backup/list",  8085),
        ("api.agentai0.com", "/api/v1/backup/upload",8085),
        ("api.agentai0.com", "/",                    8084),
        ("api.agentai0.com", "/health",              8084),
        ("api.agentai0.com", "/api/auth/user/login", 8084),
        ("api.agentai0.com", "/api/rules",           8084),
        ("api.agentai0.com", "/api/admin/stats",     8084),
        ("api.agentai0.com", "/api/ai/generate",     8084),
        ("sync.agentai0.com", "/health",             8085),
        ("sync.agentai0.com", "/auth/login",         8085),
        ("sync.agentai0.com", "/sync/push",          8085),
        ("admin.agentai0.com", "/login",             8086),
        ("admin.agentai0.com", "/dashboard",         8086),
    ]

    # 加载所有 Nginx 配置
    configs = {}
    for fname in ["api.agentai0.com.conf", "sync.agentai0.com.conf", "admin.agentai0.com.conf"]:
        sn, rules = parse_nginx_location_rules(os.path.join(NGINX_DIR, fname))
        configs[sn] = rules

    def match_backend(domain, path):
        """Nginx-style location 匹配: 最长前缀匹配."""
        if domain not in configs:
            return None
        rules = configs[domain]
        best_match = None
        best_len = -1
        for loc_path, backend in rules:
            if loc_path == "/":
                continue  # catch-all 最后处理
            if path.startswith(loc_path):
                if len(loc_path) > best_len:
                    best_match = backend
                    best_len = len(loc_path)
        if best_match is None:
            # fallback to /
            for loc_path, backend in rules:
                if loc_path == "/":
                    best_match = backend
        return best_match

    failures = []
    for domain, path, expected_port in test_cases:
        backend = match_backend(domain, path)
        expected = f"127.0.0.1:{expected_port}"
        if expected not in (backend or ""):
            failures.append(f"  {domain:25s} {path:35s} 期望 → {expected}, 实际 → {backend}")

    assert not failures, f"路由分发错误:\n" + "\n".join(failures)
    print(f"  [OK] 全部 {len(test_cases)} 个请求分发检查通过")


# ========== 测试 5: 同步服务器和主 API 路由验证 ==========

def test_sync_server_has_auth_routes():
    """同步服务器必须包含 /auth/login 等路由."""
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    sync_dir = os.path.join(project_root, "csBaby-server-py")
    routes = extract_flask_routes(
        os.path.join(sync_dir, "app.py"),
        extra_sys_path=sync_dir,
        env_overrides={"JWT_SECRET": "test-secret", "PORT": "8080"}
    )
    if "__error__" in routes:
        print(f"  [SKIP] {routes['__error__']}")
        return

    for path in ["/auth/login", "/auth/register", "/auth/refresh"]:
        assert path in routes, f"同步服务器缺失路由: {path}"
    print("  [OK] 同步服务器认证路由完整")


def test_sync_server_has_sync_routes():
    """同步服务器必须包含 /sync/push 等路由."""
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    sync_dir = os.path.join(project_root, "csBaby-server-py")
    routes = extract_flask_routes(
        os.path.join(sync_dir, "app.py"),
        extra_sys_path=sync_dir,
        env_overrides={"JWT_SECRET": "test-secret", "PORT": "8080"}
    )
    if "__error__" in routes:
        print(f"  [SKIP] {routes['__error__']}")
        return

    for path in ["/sync/push", "/sync/all", "/sync/changes", "/sync/resolve", "/sync"]:
        assert path in routes, f"同步服务器缺失同步路由: {path}"
    print("  [OK] 同步服务器同步路由完整")


def test_main_api_has_correct_routes():
    """主 API 有正确业务路由, 无 /auth/login 等同步路由."""
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    routes = extract_flask_routes(
        os.path.join(project_root, "app.py"),
        extra_sys_path=project_root,
        env_overrides={"JWT_SECRET": "test-secret", "DATABASE_PATH": ":memory:"}
    )
    if "__error__" in routes:
        print(f"  [SKIP] {routes['__error__']}")
        return

    # 主 API 应有这些路由
    assert "/api/auth/user/login" in routes, "主 API 缺失 /api/auth/user/login"
    assert "/health" in routes, "主 API 缺失 /health"

    # 主 API 不应有同步/认证路由 (它们在同步服务器)
    for path in ["/auth/login", "/sync/push"]:
        assert path not in routes, f"主 API 不应有 {path}"
    print("  [OK] 主 API 路由分布正确")


# ========== 全部测试 ==========

if __name__ == "__main__":
    tests = [
        ("Nginx 配置文件存在", test_nginx_config_files_exist),
        ("server_name 正确", test_nginx_config_server_names),
        ("api.agentai0.com 路由顺序", test_api_agentai0_routing_order),
        ("api.agentai0.com 后端地址", test_api_agentai0_backend_correct),
        ("sync/admin 配置", test_sync_admin_config),
        ("请求分发模拟", test_simulate_proxy_dispatch),
        ("同步服务器认证路由", test_sync_server_has_auth_routes),
        ("同步服务器同步路由", test_sync_server_has_sync_routes),
        ("主 API 路由分布正确", test_main_api_has_correct_routes),
    ]

    passed = 0
    failed = 0
    print(f"\n{'='*60}")
    print(f"Nginx 反向代理路由分发测试")
    print(f"{'='*60}\n")

    for name, test_fn in tests:
        try:
            test_fn()
            print(f"  [PASS] {name}")
            passed += 1
        except Exception as e:
            print(f"  [FAIL] {name}: {e}")
            failed += 1

    print(f"\n{'='*60}")
    print(f"总计: {passed}/{passed + failed} 通过", end="")
    if failed > 0:
        print(f", {failed} 失败 ❌")
    else:
        print(" ✅")
    print(f"{'='*60}")
