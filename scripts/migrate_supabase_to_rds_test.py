#!/usr/bin/env python3
"""Supabase → RDS 迁移脚本 — 单元测试

测试策略:
  - 不依赖真实数据库连接
  - 通过 mock 模拟 psycopg2 连接和游标
  - 覆盖迁移脚本的核心函数
"""
import sys
import os
import json
import pytest
from unittest.mock import MagicMock, patch, call

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from scripts.migrate_supabase_to_rds import (
    TABLES,
    get_connection,
    ensure_schema,
    get_row_count,
    get_column_names,
    compute_table_checksum,
    migrate_table,
    verify_migration,
    main,
)

# ==================== Fixtures ====================

@pytest.fixture
def mock_source_conn():
    """模拟 Supabase 连接"""
    conn = MagicMock()
    cursor = MagicMock()
    conn.cursor.return_value = cursor
    return conn

@pytest.fixture
def mock_target_conn():
    """模拟 RDS 连接"""
    conn = MagicMock()
    cursor = MagicMock()
    conn.cursor.return_value = cursor
    return conn

@pytest.fixture
def mock_psycopg2(monkeypatch):
    """模拟 psycopg2.connect"""
    mock = MagicMock()
    monkeypatch.setattr('psycopg2.connect', mock)
    return mock

# ==================== 正常场景 ====================

class TestSchemaCreation:
    """测试建表逻辑"""

    def test_ensure_schema_creates_all_tables(self, mock_target_conn):
        """正常场景1: 创建所有表"""
        mock_target_conn.cursor.return_value.execute.return_value = None

        ensure_schema(mock_target_conn)

        # 验证 execute 被调用了足够多次
        execute_calls = mock_target_conn.cursor.return_value.execute.call_count
        # 至少 TABLES 的数量次建表调用
        assert execute_calls >= len(TABLES)
        mock_target_conn.commit.assert_called()

    def test_create_all_tables_with_indexes(self, mock_target_conn):
        """正常场景2: 建表时也创建索引"""
        mock_target_conn.cursor.return_value.execute.return_value = None

        ensure_schema(mock_target_conn)

        # 验证每个表的索引也被创建
        cursor = mock_target_conn.cursor.return_value
        all_execute_args = [str(c) for c in cursor.execute.call_args_list]
        index_count = sum(len(t['indexes']) for t in TABLES)
        # execute 调用次数 ≈ 表数 + 索引数
        assert cursor.execute.call_count >= len(TABLES) + index_count

    def test_index_exception_handled_gracefully(self, mock_target_conn):
        """正常场景3: 索引已存在时不会报错中断"""
        cursor = mock_target_conn.cursor.return_value
        # 所有建表成功，每个表可能有多个索引，每个索引也可能报错
        # 只需要验证建表成功且索引报错不影响整体流程
        success_values = []
        for t in TABLES:
            success_values.append(None)  # create_sql succeeds
            for _ in t['indexes']:
                success_values.append(Exception('relation already exists'))  # index errors
        cursor.execute.side_effect = success_values

        ensure_schema(mock_target_conn)  # 不应抛出异常

    def test_create_specific_table(self, mock_target_conn):
        """正常场景4: 只创建指定表"""
        cursor = mock_target_conn.cursor.return_value
        cursor.execute.return_value = None

        ensure_schema(mock_target_conn, table_name='users')

        # 验证调用了 users 表的创建 SQL（简化匹配 — 检查表名在 SQL 中）
        execute_args = [str(c) for c in cursor.execute.call_args_list]
        user_table_calls = [a for a in execute_args if 'users' in a and 'CREATE TABLE' in a]
        assert len(user_table_calls) >= 1, f"未找到 users 表的建表 SQL, 调用: {execute_args[:5]}"
        # 确保没有创建其他表（如 keyword_rules）
        other_table_calls = [a for a in execute_args if 'CREATE' in a and 'users' not in a]
        # 可能会有索引创建，但不应该有其他 CREATE TABLE
        create_table_others = [a for a in other_table_calls if 'CREATE TABLE' in a]
        assert len(create_table_others) == 0, f"不应该创建其他表: {create_table_others}"


class TestCountAndChecksum:
    """测试行数统计和校验"""

    def test_get_row_count(self, mock_source_conn):
        """正常场景5: 获取行数"""
        cursor = mock_source_conn.cursor.return_value
        cursor.fetchone.return_value = (42,)

        count = get_row_count(mock_source_conn, 'keyword_rules')

        assert count == 42
        cursor.execute.assert_called_with('SELECT COUNT(*) FROM keyword_rules')

    def test_get_column_names(self, mock_source_conn):
        """正常场景6: 获取列名"""
        cursor = mock_source_conn.cursor.return_value
        cursor.fetchall.return_value = [
            ('id',), ('keyword',), ('match_type',), ('reply_template',)
        ]

        columns = get_column_names(mock_source_conn, 'keyword_rules')

        assert len(columns) == 4
        assert 'id' in columns
        assert 'keyword' in columns

    def test_empty_table_checksum(self, mock_source_conn):
        """正常场景7: 空表的校验和"""
        cursor = mock_source_conn.cursor.return_value
        cursor.fetchone.side_effect = [
            (0,),  # COUNT
            ('EMPTY',),  # 实际不会走到这里
        ]

        checksum, count = compute_table_checksum(mock_source_conn, 'empty_table')

        assert checksum == 'EMPTY'
        assert count == 0


class TestMigration:
    """测试数据迁移"""

    def test_migrate_table_dry_run(self, mock_source_conn, mock_target_conn):
        """正常场景8: dry-run 模式"""
        cursor = mock_source_conn.cursor.return_value
        cursor.fetchone.return_value = (10,)

        table_def = [t for t in TABLES if t['name'] == 'users'][0]
        result = migrate_table(mock_source_conn, mock_target_conn, table_def, dry_run=True)

        assert result['table'] == 'users'
        assert result['source_rows'] == 10
        assert result['migrated'] is False
        # dry_run 不应调用目标库写入
        mock_target_conn.cursor.return_value.execute.assert_not_called()

    def test_migrate_table_with_data(self, mock_source_conn, mock_target_conn, monkeypatch):
        """正常场景9: 迁移有数据的表"""
        # 模拟 psycopg2.extras.execute_batch
        monkeypatch.setattr('psycopg2.extras.execute_batch', lambda cursor, sql, rows: None)

        # 配置 source 连接
        src_cursor = mock_source_conn.cursor.return_value
        src_cursor.fetchone.return_value = (2,)  # COUNT
        src_cursor.fetchall.return_value = [
            ('id',), ('keyword',), ('match_type',), ('reply_template',),
            ('category',), ('target_type',), ('target_names_json',),
            ('priority',), ('enabled',), ('created_at',), ('updated_at',),
            ('tenant_id',), ('sync_version',), ('deleted',),
        ]
        src_cursor.__iter__.return_value = [
            ('r1', 'hello', 'CONTAINS', 'Hi', '', 'ALL', '[]', 0, True, 1000, 1000, 't1', 1000, False),
            ('r2', 'bye', 'EXACT', 'Bye', '', 'ALL', '[]', 1, True, 1001, 1001, 't1', 1001, False),
        ]

        # 配置 target 连接 — 初始空表
        tgt_cursor = mock_target_conn.cursor.return_value
        tgt_cursor.fetchone.return_value = (0,)  # 目标表为空

        table_def = [t for t in TABLES if t['name'] == 'keyword_rules'][0]
        result = migrate_table(mock_source_conn, mock_target_conn, table_def, dry_run=False)

        assert result['table'] == 'keyword_rules'
        assert result['source_rows'] == 2
        assert result['migrated'] is True

    def test_skip_when_row_count_matches(self, mock_source_conn, mock_target_conn):
        """正常场景10: 行数一致时跳过迁移"""
        source_cursor = mock_source_conn.cursor.return_value
        target_cursor = mock_target_conn.cursor.return_value
        # 两端行数一致 (source fetchone 用于 get_row_count 源表)
        # 注意: get_row_count 会调用两次 — 先源表再目标表
        source_cursor.fetchone.return_value = (10,)
        target_cursor.fetchone.return_value = (10,)

        table_def = [t for t in TABLES if t['name'] == 'users'][0]
        result = migrate_table(mock_source_conn, mock_target_conn, table_def, dry_run=False)

        assert result['migrated'] is False
        # 不应调用 INSERT/UPDATE/SELECT * 等数据操作（COUNT 除外）
        non_count_calls = [
            c for c in target_cursor.execute.call_args_list
            if 'COUNT' not in str(c)
        ]
        assert len(non_count_calls) == 0, f"存在非 COUNT 的调用: {non_count_calls}"


class TestVerification:
    """测试迁移验证"""

    def test_verify_row_count_match(self, mock_source_conn, mock_target_conn):
        """正常场景11: 行数一致"""
        src_cursor = mock_source_conn.cursor.return_value
        tgt_cursor = mock_target_conn.cursor.return_value
        src_cursor.fetchone.return_value = (10,)
        tgt_cursor.fetchone.return_value = (10,)

        vr = verify_migration(mock_source_conn, mock_target_conn, 'users')

        assert vr['status'] == 'PASS'
        assert vr['source_rows'] == 10
        assert vr['target_rows'] == 10

    def test_verify_empty_table(self, mock_source_conn, mock_target_conn):
        """正常场景12: 空表验证"""
        src_cursor = mock_source_conn.cursor.return_value
        tgt_cursor = mock_target_conn.cursor.return_value
        src_cursor.fetchone.return_value = (0,)
        tgt_cursor.fetchone.return_value = (0,)

        vr = verify_migration(mock_source_conn, mock_target_conn, 'empty_table')

        assert vr['status'] == 'PASS'
        assert vr['source_rows'] == 0
        assert vr['target_rows'] == 0


# ==================== 边界值场景 ====================

class TestBoundaryCases:
    """边界值测试"""

    def test_table_def_has_all_required_fields(self):
        """边界值1: 所有表定义包含必要字段"""
        for t in TABLES:
            assert 'name' in t, f"表 {t} 缺少 name"
            assert 'create_sql' in t, f"表 {t.get('name')} 缺少 create_sql"
            assert 'select_sql' in t, f"表 {t.get('name')} 缺少 select_sql"
            assert 'batch_size' in t, f"表 {t.get('name')} 缺少 batch_size"
            assert 'indexes' in t, f"表 {t.get('name')} 缺少 indexes"
            assert isinstance(t['batch_size'], int) and t['batch_size'] > 0

    def test_at_least_8_tables_defined(self):
        """边界值2: 至少定义了8张表"""
        assert len(TABLES) >= 8, f"只有 {len(TABLES)} 张表，预期至少8张"

    def test_all_table_names_are_unique(self):
        """边界值3: 表名唯一"""
        names = [t['name'] for t in TABLES]
        assert len(names) == len(set(names)), "存在重复的表名"

    def test_batch_size_does_not_exceed_500(self):
        """边界值4: batch_size 不超过500"""
        for t in TABLES:
            assert t['batch_size'] <= 500, f"表 {t['name']} 的 batch_size 超过500"


# ==================== 异常场景 ====================

class TestExceptionCases:
    """异常场景测试"""

    def test_connect_with_invalid_url(self, mock_psycopg2):
        """异常1: 连接无效 URL"""
        mock_psycopg2.side_effect = Exception('could not connect to server')

        with pytest.raises(Exception, match='could not connect to server'):
            get_connection('postgresql://invalid:invalid@nonexistent:5432/db')

    def test_main_missing_supabase_url(self, monkeypatch):
        """异常2: 缺少 Supabase URL"""
        monkeypatch.delenv('SUPABASE_DB_URL', raising=False)
        monkeypatch.delenv('RDS_DB_URL', raising=False)

        with patch('sys.argv', ['migrate_supabase_to_rds.py']):
            with pytest.raises(SystemExit):
                main()

    def test_main_missing_rds_url(self, monkeypatch):
        """异常3: 缺少 RDS URL"""
        monkeypatch.setenv('SUPABASE_DB_URL', 'postgresql://supabase:5432/db')
        monkeypatch.delenv('RDS_DB_URL', raising=False)

        with patch('sys.argv', ['migrate_supabase_to_rds.py']):
            with pytest.raises(SystemExit):
                main()

    def test_row_count_mismatch_detected(self, mock_source_conn, mock_target_conn):
        """异常4: 行数不匹配"""
        src_cursor = mock_source_conn.cursor.return_value
        tgt_cursor = mock_target_conn.cursor.return_value
        src_cursor.fetchone.return_value = (100,)
        tgt_cursor.fetchone.return_value = (98,)

        vr = verify_migration(mock_source_conn, mock_target_conn, 'keyword_rules')

        assert vr['status'] == 'FAIL'
        assert '行数不匹配' in vr['message']

    def test_table_creation_failure(self, mock_target_conn):
        """异常5: 建表失败"""
        cursor = mock_target_conn.cursor.return_value
        cursor.execute.side_effect = Exception('permission denied for schema')

        with pytest.raises(Exception, match='permission denied for schema'):
            ensure_schema(mock_target_conn)


# ==================== 配置完整性测试 ====================

class TestConfigIntegrity:
    """配置完整性测试"""

    def test_select_sql_includes_all_columns(self):
        """配置测试: select_sql 使用 SELECT *"""
        for t in TABLES:
            assert t['select_sql'].startswith('SELECT *'), \
                f"表 {t['name']} 的 select_sql 没有使用 SELECT *"

    def test_create_sql_includes_create_table(self):
        """配置测试: create_sql 包含 CREATE TABLE"""
        for t in TABLES:
            assert 'CREATE TABLE' in t['create_sql'].upper(), \
                f"表 {t['name']} 的 create_sql 缺少 CREATE TABLE"


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
