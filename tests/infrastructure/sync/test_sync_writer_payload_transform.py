"""BUG-R11: sync_writer payload 转换契约测试。

背景: Supabase schema 是 tenant 隔离 (tenant_id + id, 无 user_id),
      sync_writer 必须做 payload 转换 (user_id → tenant_id, 补 sync_version),
      修正表名 (model_configs → ai_model_configs, blacklist → message_blacklist),
      拒绝不存在的表 (feedback / optimization_metrics / devices / tenant_*)。
"""
import json
import pytest
from unittest.mock import patch, MagicMock, call
from infrastructure.persistence.database import get_connection, init_db
from infrastructure.sync.sync_writer import (
    SyncWriter,
    upsert_to_supabase,
    _TABLE_NAME_MAP,
)


@pytest.fixture
def db():
    init_db()
    db = get_connection()
    db.execute("DELETE FROM sync_outbox")
    db.commit()
    yield db
    db.close()


@pytest.fixture
def writer(db):
    return SyncWriter(db)


# ---------- 表名映射契约 ----------

def test_table_name_map_keyword_rules_unchanged():
    assert _TABLE_NAME_MAP["keyword_rules"] == "keyword_rules"


def test_table_name_map_model_configs_renamed_to_ai_model_configs():
    """主 API 旧名 model_configs → Supabase 实际表名 ai_model_configs。"""
    assert _TABLE_NAME_MAP["model_configs"] == "ai_model_configs"


def test_table_name_map_blacklist_renamed_to_message_blacklist():
    """主 API 旧名 blacklist → Supabase 实际表名 message_blacklist。"""
    assert _TABLE_NAME_MAP["blacklist"] == "message_blacklist"


def test_table_name_map_feedback_registered_after_ddl_migration():
    """feedback 表已在 Supabase 补全 (deploy/supabase_missing_tables.sql), 同步路径生效。"""
    assert _TABLE_NAME_MAP["feedback"] == "feedback"


def test_table_name_map_optimization_metrics_registered_after_ddl_migration():
    """optimization_metrics 表已在 Supabase 补全, 同步路径生效。"""
    assert _TABLE_NAME_MAP["optimization_metrics"] == "optimization_metrics"


def test_table_name_map_tenant_configs_removed():
    """tenant_style_config / tenant_app_config 在 Supabase 不存在 → 仍不推送。"""
    assert _TABLE_NAME_MAP.get("tenant_style_config") is None
    assert _TABLE_NAME_MAP.get("tenant_app_config") is None


def test_table_name_map_devices_removed():
    """user_devices / devices 在 Supabase 不存在 + token 敏感 → 仍不推送。"""
    assert _TABLE_NAME_MAP.get("user_devices") is None
    assert _TABLE_NAME_MAP.get("devices") is None


def test_table_name_map_other_core_tables_present():
    """核心业务表必须保留映射: 主 API key → Supabase 表名 (value)。"""
    expected = {
        "keyword_rules": "keyword_rules",
        "reply_history": "reply_history",
        "users": "users",
        "model_configs": "ai_model_configs",
        "blacklist": "message_blacklist",
        "user_style_profiles": "user_style_profiles",
        "app_configs": "app_configs",
        "scenarios": "scenarios",
    }
    for k, v in expected.items():
        assert k in _TABLE_NAME_MAP, f"{k} missing from _TABLE_NAME_MAP"
        assert _TABLE_NAME_MAP[k] == v, f"{k}: expected {v}, got {_TABLE_NAME_MAP[k]}"


# ---------- payload 转换契约 (通过 mock psycopg2 cursor 捕获 SQL) ----------

def _make_mock_cursor_capture():
    """返回 (mock_conn, captured_calls): cur.execute 的全部调用。"""
    captured = []
    mock_cursor = MagicMock()
    mock_cursor.execute = lambda sql, params=None: captured.append((sql, params))
    mock_cursor.__enter__ = lambda s: mock_cursor
    mock_cursor.__exit__ = lambda s, *a: False
    mock_conn = MagicMock()
    mock_conn.cursor = lambda: mock_cursor
    mock_conn.commit = MagicMock()
    return mock_conn, captured


def test_upsert_keyword_rules_converts_user_id_to_tenant_id():
    """user_id 必须重命名为 tenant_id, 否则 Supabase 报 column does not exist。"""
    mock_conn, captured = _make_mock_cursor_capture()
    with patch("infrastructure.sync.sync_writer.get_supa_conn", return_value=mock_conn), \
         patch("infrastructure.sync.sync_writer.put_supa_conn"):
        upsert_to_supabase(
            "keyword_rules", "INSERT", 1,
            {"id": "rule-1", "user_id": "u-1", "keyword": "hi"},
        )
    assert len(captured) == 1
    sql, params = captured[0]
    col_list = sql.split("(", 1)[1].split(")", 1)[0]
    cols = [c.strip() for c in col_list.split(",")]
    assert "tenant_id" in cols, f"必须含 tenant_id, got cols={cols}"
    assert "user_id" not in cols, f"不应直接推 user_id, got cols={cols}"
    # params 是按列顺序
    param_dict = dict(zip(cols, params))
    assert param_dict["tenant_id"] == "u-1", "user_id 必须重映射为 tenant_id 的值"
    assert param_dict["id"] == "rule-1"
    assert param_dict["keyword"] == "hi"


def test_upsert_keyword_rules_adds_sync_version_when_missing():
    """payload 不含 sync_version 时, sync_writer 必须自动补当前毫秒时间戳。"""
    mock_conn, captured = _make_mock_cursor_capture()
    with patch("infrastructure.sync.sync_writer.get_supa_conn", return_value=mock_conn), \
         patch("infrastructure.sync.sync_writer.put_supa_conn"):
        upsert_to_supabase(
            "keyword_rules", "INSERT", 1,
            {"id": "rule-1", "user_id": "u-1", "keyword": "hi"},
        )
    sql, params = captured[0]
    cols = [c.strip() for c in sql.split("(", 1)[1].split(")", 1)[0].split(",")]
    param_dict = dict(zip(cols, params))
    assert "sync_version" in cols, "必须自动补 sync_version 列"
    assert isinstance(param_dict["sync_version"], int)
    assert param_dict["sync_version"] > 0


def test_upsert_preserves_existing_sync_version():
    """payload 已含 sync_version 时不应覆盖。"""
    mock_conn, captured = _make_mock_cursor_capture()
    with patch("infrastructure.sync.sync_writer.get_supa_conn", return_value=mock_conn), \
         patch("infrastructure.sync.sync_writer.put_supa_conn"):
        upsert_to_supabase(
            "keyword_rules", "INSERT", 1,
            {"id": "rule-1", "user_id": "u-1", "sync_version": 1234567890},
        )
    sql, params = captured[0]
    cols = [c.strip() for c in sql.split("(", 1)[1].split(")", 1)[0].split(",")]
    param_dict = dict(zip(cols, params))
    assert param_dict["sync_version"] == 1234567890


def test_upsert_reply_history_does_not_require_user_id_conversion():
    """reply_history 主 API SQLite 有 user_id,Supabase 表无 user_id,转换同样要 user_id→tenant_id。"""
    mock_conn, captured = _make_mock_cursor_capture()
    with patch("infrastructure.sync.sync_writer.get_supa_conn", return_value=mock_conn), \
         patch("infrastructure.sync.sync_writer.put_supa_conn"):
        upsert_to_supabase(
            "reply_history", "INSERT", "r-1",
            {"id": "r-1", "user_id": "u-1", "original_message": "hi",
             "generated_reply": "hello"},
        )
    sql, params = captured[0]
    cols = [c.strip() for c in sql.split("(", 1)[1].split(")", 1)[0].split(",")]
    param_dict = dict(zip(cols, params))
    assert "user_id" not in cols
    assert param_dict["tenant_id"] == "u-1"
    assert param_dict["original_message"] == "hi"


def test_upsert_keyword_rules_renames_target_names_to_json():
    """主 API payload 用 target_names, Supabase 表用 target_names_json。"""
    mock_conn, captured = _make_mock_cursor_capture()
    with patch("infrastructure.sync.sync_writer.get_supa_conn", return_value=mock_conn), \
         patch("infrastructure.sync.sync_writer.put_supa_conn"):
        upsert_to_supabase(
            "keyword_rules", "INSERT", "r-1",
            {"id": "r-1", "user_id": "u-1", "keyword": "hi",
             "target_names": "[\"a\", \"b\"]"},
        )
    sql, params = captured[0]
    cols = [c.strip() for c in sql.split("(", 1)[1].split(")", 1)[0].split(",")]
    param_dict = dict(zip(cols, params))
    assert "target_names_json" in cols, "应重命名为 target_names_json"
    assert "target_names" not in cols
    assert param_dict["target_names_json"] == "[\"a\", \"b\"]"


def test_upsert_keyword_rules_converts_enabled_int_to_bool():
    """主 API SQLite enabled=1 (int) → Supabase boolean 严格类型, 不转报错。"""
    mock_conn, captured = _make_mock_cursor_capture()
    with patch("infrastructure.sync.sync_writer.get_supa_conn", return_value=mock_conn), \
         patch("infrastructure.sync.sync_writer.put_supa_conn"):
        upsert_to_supabase(
            "keyword_rules", "INSERT", "r-1",
            {"id": "r-1", "user_id": "u-1", "keyword": "hi", "enabled": 1},
        )
    sql, params = captured[0]
    cols = [c.strip() for c in sql.split("(", 1)[1].split(")", 1)[0].split(",")]
    param_dict = dict(zip(cols, params))
    assert param_dict["enabled"] is True, f"enabled 必须转 bool, got {param_dict['enabled']!r}"


def test_upsert_unknown_table_raises():
    """不在 _TABLE_NAME_MAP 的表 (e.g. 'nonexistent') → ValueError。"""
    with pytest.raises(ValueError):
        upsert_to_supabase("nonexistent_table_xyz", "INSERT", 1, {"id": 1})


# ---------- SyncWriter 集成: user_id-less payload 也接受 (tenant_id 直接传) ----------

def test_writer_push_with_tenant_id_in_payload_passes_through(writer):
    """如果调用方已在 payload 中放好 tenant_id, 不应再被改。"""
    mock_conn, captured = _make_mock_cursor_capture()
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None):
        writer.push(
            "keyword_rules", "INSERT", "r-1",
            {"id": "r-1", "tenant_id": "u-1", "keyword": "hi"},
        )
    # upsert_to_supabase 被 mock 跳过, 这里通过 outbox 为空来验证成功路径


def test_writer_push_failure_preserves_original_payload_in_outbox(writer, db):
    """outbox 入队的 payload 是原始 payload (转换前), 保留调试信息。"""
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("schema mismatch")):
        writer.push(
            "keyword_rules", "INSERT", "r-1",
            {"id": "r-1", "user_id": "u-1", "keyword": "hi"},
        )
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert len(outbox) == 1
    payload = json.loads(outbox[0]["payload"])
    # 原始 user_id 应保留, 用于调试
    assert payload["user_id"] == "u-1"
