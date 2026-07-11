#!/usr/bin/env python3
"""
Patch main API app.py to add:
- /auth/refresh
- /sync/all, /sync/changes, /sync/push
- /api/v1/backup/upload, /api/v1/backup/list, /api/v1/backup/download/{id}
"""

import sys

PATCH_MARKER = "# ========== Cloud Sync Compatibility Routes (added by refactor) =========="

ROUTES = '''
import time
from datetime import datetime

# ========== Cloud Sync Compatibility Routes (added by refactor) ==========
# These routes provide backward compatibility with the legacy sync server (csBaby-server-py)
# so the Android client can use a single domain (api.agentai0.com) for all API calls.
# nginx routes /auth/* and /sync/* and /api/v1/backup/* to this container (8084).

@app.route("/auth/refresh", methods=["POST"])
def auth_refresh():
    """
    Refresh access token via refreshToken.
    Request: {"refreshToken": "..."}
    Response: {userId, token (new), refreshToken (new), expiresIn}
    """
    data = request.get_json(force=True, silent=True) or {}
    refresh_token = data.get("refreshToken")
    if not refresh_token:
        return jsonify({"error": "Missing refreshToken"}), 400

    # Verify the refresh token and get user info
    try:
        payload = jwt.decode(refresh_token, JWT_SECRET, algorithms=["HS256"])
        user_id = payload.get("user_id")
        tenant_id = payload.get("tenant_id", user_id)
        token_type = payload.get("type")
        if token_type != "refresh":
            return jsonify({"error": "Invalid token type"}), 401
    except jwt.ExpiredSignatureError:
        return jsonify({"error": "Refresh token expired"}), 401
    except (jwt.InvalidTokenError, Exception) as e:
        return jsonify({"error": f"Invalid refresh token: {e}"}), 401

    # Issue new tokens
    now = int(time.time())
    access_payload = {
        "user_id": user_id, "tenant_id": tenant_id, "type": "access",
        "iat": now, "exp": now + 24 * 60 * 60,
    }
    new_access = jwt.encode(access_payload, JWT_SECRET, algorithm="HS256")
    refresh_payload = {
        "user_id": user_id, "tenant_id": tenant_id, "type": "refresh",
        "iat": now, "exp": now + 30 * 24 * 60 * 60,
    }
    new_refresh = jwt.encode(refresh_payload, JWT_SECRET, algorithm="HS256")

    return jsonify({
        "userId": user_id,
        "tenantId": tenant_id,
        "token": new_access,
        "refreshToken": new_refresh,
        "expiresIn": 30 * 24 * 60 * 60,
    })


@app.route("/sync/all", methods=["GET"])
@require_auth
def sync_all():
    """
    Full sync: return all tenant data.
    Query: tenantId=<tenant_id>
    Response: {rules, models, blacklist, history, scenarios, appConfigs, userStyle}
    """
    ensure_db()
    tenant_id = request.args.get("tenantId") or request.user_id
    conn = get_connection()
    try:
        rules = conn.execute(
            "SELECT * FROM keyword_rules WHERE tenant_id = ?", (tenant_id,)
        ).fetchall()
        models = conn.execute(
            "SELECT * FROM model_configs WHERE tenant_id = ?", (tenant_id,)
        ).fetchall()
        bl = conn.execute(
            "SELECT * FROM blacklist WHERE tenant_id = ?", (tenant_id,)
        ).fetchall()
        history = conn.execute(
            "SELECT * FROM reply_history WHERE tenant_id = ?", (tenant_id,)
        ).fetchall()
        scenarios = conn.execute(
            "SELECT * FROM scenarios WHERE tenant_id = ?", (tenant_id,)
        ).fetchall()
        style = conn.execute(
            "SELECT * FROM user_style_profiles WHERE tenant_id = ?", (tenant_id,)
        ).fetchall()
        metrics = conn.execute(
            "SELECT * FROM optimization_metrics WHERE tenant_id = ?", (tenant_id,)
        ).fetchall()
        return jsonify({
            "rules": [dict_from_row(r) for r in rules],
            "models": [dict_from_row(r) for r in models],
            "blacklist": [dict_from_row(r) for r in bl],
            "history": [dict_from_row(r) for r in history],
            "scenarios": [dict_from_row(r) for r in scenarios],
            "userStyle": [dict_from_row(r) for r in style],
            "metrics": [dict_from_row(r) for r in metrics],
        })
    finally:
        conn.close()


@app.route("/sync/changes", methods=["GET"])
@require_auth
def sync_changes():
    """
    Incremental sync: return changes since a timestamp.
    Query: tenantId=<tenant_id>, since=<unix_ms>
    """
    ensure_db()
    tenant_id = request.args.get("tenantId") or request.user_id
    since = request.args.get("since", "0")
    conn = get_connection()
    try:
        rules = conn.execute(
            "SELECT * FROM keyword_rules WHERE tenant_id = ? AND updated_at > ?",
            (tenant_id, since)
        ).fetchall()
        return jsonify({
            "rules": [dict_from_row(r) for r in rules],
            "deletedIds": [],
        })
    finally:
        conn.close()


@app.route("/sync/push", methods=["POST"])
@require_auth
def sync_push():
    """
    Push client changes to server.
    Body: {rules: [...], models: [...]}
    Response: {applied: N}
    """
    ensure_db()
    tenant_id = request.user_id
    data = request.get_json(force=True, silent=True) or {}
    conn = get_connection()
    applied = 0
    try:
        for rule in data.get("rules", []):
            cols = list(rule.keys())
            vals = [rule[c] for c in cols]
            set_clause = ", ".join(f"{c}=?" for c in cols)
            conn.execute(
                f"INSERT OR REPLACE INTO keyword_rules ({','.join(cols)}) VALUES ({','.join(['?']*len(cols))})",
                vals
            )
            applied += 1
        conn.commit()
        return jsonify({"applied": applied})
    finally:
        conn.close()


# ========== Backup routes (v1 legacy path compatibility) ==========

_in_memory_backups = {}  # {backup_id: {tenantId, data, created}}


@app.route("/api/v1/backup/upload", methods=["POST"])
@require_auth
def backup_upload_v1():
    """
    Upload a backup. Body: {deviceName, data, checksum, appVersion}
    Response: {backupId, createdAt}
    """
    data = request.get_json(force=True, silent=True) or {}
    if not isinstance(data, dict) or "data" not in data:
        return jsonify({"error": "Missing backup data"}), 400
    backup_id = secrets.token_hex(16)
    _in_memory_backups[backup_id] = {
        "tenantId": request.user_id,
        "data": data.get("data"),
        "deviceName": data.get("deviceName", "android"),
        "checksum": data.get("checksum", ""),
        "appVersion": data.get("appVersion", ""),
        "created": int(datetime.utcnow().timestamp() * 1000),
    }
    return jsonify({
        "backupId": backup_id,
        "createdAt": _in_memory_backups[backup_id]["created"],
    })


@app.route("/api/v1/backup/list", methods=["GET"])
@require_auth
def backup_list_v1():
    """List all backups for the current tenant."""
    tenant_id = request.user_id
    items = [
        {"backupId": bid, **{k: v for k, v in b.items() if k != "data"}}
        for bid, b in _in_memory_backups.items()
        if b.get("tenantId") == tenant_id
    ]
    return jsonify({"items": items})


@app.route("/api/v1/backup/download/<backup_id>", methods=["GET"])
@require_auth
def backup_download_v1(backup_id):
    """Download a specific backup by ID."""
    backup = _in_memory_backups.get(backup_id)
    if not backup or backup.get("tenantId") != request.user_id:
        return jsonify({"error": "Backup not found"}), 404
    return jsonify(backup.get("data", {}))


# ========== End Cloud Sync Compatibility Routes ==========
'''

if __name__ == "__main__":
    app_py_path = sys.argv[1] if len(sys.argv) > 1 else "/app/app.py"
    with open(app_py_path, "r") as f:
        content = f.read()
    if PATCH_MARKER in content:
        print("Patch already applied, skipping.")
        sys.exit(0)
    with open(app_py_path, "a") as f:
        f.write("\n\n" + ROUTES)
    print(f"Patched {app_py_path} with cloud sync compatibility routes.")
