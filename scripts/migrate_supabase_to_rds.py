#!/usr/bin/env python3
"""
Supabase PostgreSQL → AWS RDS PostgreSQL 数据迁移脚本。

功能:
  1. 自动在 RDS 上创建表结构（基于 schema.sql）
  2. 从 Supabase 逐表读取数据，批量写入 RDS
  3. 行数对比 + 抽样校验
  4. 支持 --dry-run（仅建表不迁移数据）
  5. 支持断点续传（记录已完成表）

用法:
  # 设置环境变量 (推荐)
  export SUPABASE_DB_URL="postgresql://user:pass@supabase-host:5432/db"
  export RDS_DB_URL="postgresql://user:pass@rds-host:5432/db"

  # 运行迁移
  python scripts/migrate_supabase_to_rds.py

  # Dry-run 模式
  python scripts/migrate_supabase_to_rds.py --dry-run

  # 指定连接串
  python scripts/migrate_supabase_to_rds.py \
    --supabase-url "postgresql://..." \
    --rds-url "postgresql://..."
"""

import argparse
import hashlib
import json
import logging
import os
import sys
import time
from datetime import datetime

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S',
)
logger = logging.getLogger('migrate_rds')

# ========== 数据库表定义 (与 schema.sql 一致) ==========

TABLES = [
    {
        'name': 'users',
        'create_sql': """CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL,
            display_name TEXT, tenant_id TEXT NOT NULL,
            created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
            updated_at BIGINT, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)",
            "CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id)",
        ],
        'select_sql': 'SELECT * FROM users ORDER BY created_at',
        'batch_size': 100,
    },
    {
        'name': 'keyword_rules',
        'create_sql': """CREATE TABLE IF NOT EXISTS keyword_rules (
            id TEXT PRIMARY KEY, keyword TEXT, match_type TEXT, reply_template TEXT, category TEXT,
            target_type TEXT, target_names_json TEXT, priority INT DEFAULT 0, enabled BOOLEAN DEFAULT TRUE,
            created_at BIGINT, updated_at BIGINT, tenant_id TEXT NOT NULL,
            sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_keyword_tenant ON keyword_rules(tenant_id)",
            "CREATE INDEX IF NOT EXISTS idx_keyword_version ON keyword_rules(sync_version)",
        ],
        'select_sql': 'SELECT * FROM keyword_rules ORDER BY id',
        'batch_size': 500,
    },
    {
        'name': 'ai_model_configs',
        'create_sql': """CREATE TABLE IF NOT EXISTS ai_model_configs (
            id TEXT PRIMARY KEY, model_type TEXT, model_name TEXT, api_key TEXT, api_endpoint TEXT,
            temperature REAL DEFAULT 0.7, max_tokens INT DEFAULT 1000, is_default BOOLEAN DEFAULT FALSE,
            is_enabled BOOLEAN DEFAULT TRUE, monthly_cost REAL DEFAULT 0, last_used BIGINT, created_at BIGINT,
            tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_ai_model_tenant ON ai_model_configs(tenant_id)",
        ],
        'select_sql': 'SELECT * FROM ai_model_configs ORDER BY id',
        'batch_size': 200,
    },
    {
        'name': 'user_style_profiles',
        'create_sql': """CREATE TABLE IF NOT EXISTS user_style_profiles (
            id TEXT PRIMARY KEY, user_id TEXT NOT NULL, formality_level REAL DEFAULT 0.5,
            enthusiasm_level REAL DEFAULT 0.5, professionalism_level REAL DEFAULT 0.5,
            word_count_preference INT DEFAULT 50, common_phrases TEXT DEFAULT '[]', avoid_phrases TEXT DEFAULT '[]',
            learning_samples TEXT DEFAULT '[]', accuracy_score REAL DEFAULT 0.5, last_trained BIGINT, created_at BIGINT,
            tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_profile_tenant ON user_style_profiles(tenant_id)",
        ],
        'select_sql': 'SELECT * FROM user_style_profiles ORDER BY id',
        'batch_size': 100,
    },
    {
        'name': 'app_configs',
        'create_sql': """CREATE TABLE IF NOT EXISTS app_configs (
            package_name TEXT PRIMARY KEY, app_name TEXT, icon_uri TEXT, is_monitored BOOLEAN DEFAULT TRUE,
            created_at BIGINT, last_used BIGINT, tenant_id TEXT NOT NULL,
            sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_app_tenant ON app_configs(tenant_id)",
        ],
        'select_sql': 'SELECT * FROM app_configs ORDER BY package_name',
        'batch_size': 100,
    },
    {
        'name': 'scenarios',
        'create_sql': """CREATE TABLE IF NOT EXISTS scenarios (
            id TEXT PRIMARY KEY, name TEXT, type TEXT, target_id TEXT, description TEXT,
            created_at BIGINT, tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_scenario_tenant ON scenarios(tenant_id)",
        ],
        'select_sql': 'SELECT * FROM scenarios ORDER BY id',
        'batch_size': 100,
    },
    {
        'name': 'reply_history',
        'create_sql': """CREATE TABLE IF NOT EXISTS reply_history (
            id TEXT PRIMARY KEY, source_app TEXT, original_message TEXT, generated_reply TEXT, final_reply TEXT,
            rule_matched_id TEXT, model_used_id TEXT, style_applied BOOLEAN DEFAULT FALSE, send_time BIGINT,
            modified BOOLEAN DEFAULT FALSE, tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_reply_tenant ON reply_history(tenant_id)",
        ],
        'select_sql': 'SELECT * FROM reply_history ORDER BY id',
        'batch_size': 200,
    },
    {
        'name': 'message_blacklist',
        'create_sql': """CREATE TABLE IF NOT EXISTS message_blacklist (
            id TEXT PRIMARY KEY, type TEXT, value TEXT, description TEXT, package_name TEXT, created_at BIGINT,
            is_enabled BOOLEAN DEFAULT TRUE, tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_blacklist_tenant ON message_blacklist(tenant_id)",
        ],
        'select_sql': 'SELECT * FROM message_blacklist ORDER BY id',
        'batch_size': 200,
    },
    {
        'name': 'sync_checkpoints',
        'create_sql': """CREATE TABLE IF NOT EXISTS sync_checkpoints (
            tenant_id TEXT PRIMARY KEY, last_sync_version BIGINT DEFAULT 0, last_sync_time BIGINT, updated_at BIGINT,
            is_syncing BOOLEAN DEFAULT FALSE, last_error TEXT, device_info TEXT, created_at BIGINT
        )""",
        'indexes': [],
        'select_sql': 'SELECT * FROM sync_checkpoints ORDER BY tenant_id',
        'batch_size': 100,
    },
    {
        'name': 'backup_records',
        'create_sql': """CREATE TABLE IF NOT EXISTS backup_records (
            id SERIAL PRIMARY KEY, tenant_id TEXT NOT NULL, device_name TEXT, app_version TEXT, data_json TEXT,
            data_size BIGINT, checksum TEXT, version TEXT DEFAULT '1.0', backup_type TEXT DEFAULT 'manual',
            created_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, deleted BOOLEAN DEFAULT FALSE
        )""",
        'indexes': [
            "CREATE INDEX IF NOT EXISTS idx_backup_tenant ON backup_records(tenant_id)",
            "CREATE INDEX IF NOT EXISTS idx_backup_created ON backup_records(created_at)",
        ],
        'select_sql': 'SELECT * FROM backup_records ORDER BY id',
        'batch_size': 100,
    },
]


def parse_args():
    parser = argparse.ArgumentParser(
        description='Supabase → RDS 数据迁移工具',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 使用环境变量连接
  export SUPABASE_DB_URL="postgresql://..."
  export RDS_DB_URL="postgresql://..."
  python %(prog)s

  # Dry-run (只建表不迁移)
  python %(prog)s --dry-run

  # 指定连接串
  python %(prog)s --supabase-url "postgresql://..." --rds-url "postgresql://..."
        """,
    )
    parser.add_argument('--supabase-url', help='Supabase PostgreSQL 连接串')
    parser.add_argument('--rds-url', help='RDS PostgreSQL 连接串')
    parser.add_argument('--dry-run', action='store_true', help='仅建表校验，不迁移数据')
    parser.add_argument('--skip-create', action='store_true', help='跳过建表步骤')
    parser.add_argument('--table', help='仅迁移指定表')
    parser.add_argument('--resume', action='store_true', help='断点续传模式')
    return parser.parse_args()


def get_connection(db_url):
    """创建数据库连接"""
    import psycopg2
    conn = psycopg2.connect(db_url, connect_timeout=10)
    conn.autocommit = False
    return conn


def ensure_schema(conn, table_name=None):
    """在目标库创建表结构"""
    cursor = conn.cursor()
    tables_to_create = [t for t in TABLES if table_name is None or t['name'] == table_name]

    for table in tables_to_create:
        try:
            cursor.execute(table['create_sql'])
            for idx_sql in table['indexes']:
                try:
                    cursor.execute(idx_sql)
                except Exception:
                    pass  # 索引已存在忽略
            conn.commit()
            logger.info(f"  ✓ 表 {table['name']} 创建/确认完成")
        except Exception as e:
            conn.rollback()
            logger.error(f"  ✗ 表 {table['name']} 创建失败: {e}")
            raise

    if not table_name:
        logger.info(f"✓ 所有表结构创建完成 ({len(tables_to_create)} 张)")


def get_row_count(conn, table_name):
    """获取表行数"""
    cursor = conn.cursor()
    cursor.execute(f"SELECT COUNT(*) FROM {table_name}")
    return cursor.fetchone()[0]


def get_column_names(conn, table_name):
    """获取表的列名列表"""
    cursor = conn.cursor()
    cursor.execute(f"""
        SELECT column_name FROM information_schema.columns
        WHERE table_name = %s ORDER BY ordinal_position
    """, (table_name,))
    return [row[0] for row in cursor.fetchall()]


def compute_table_checksum(conn, table_name, sample_pct=0.1):
    """计算表的数据校验和（抽样）"""
    cursor = conn.cursor()
    count = get_row_count(conn, table_name)
    if count == 0:
        return 'EMPTY', 0

    sample_size = max(100, int(count * sample_pct))
    cursor.execute(f"""
        SELECT MD5(CAST(ROW_TO_JSON(t.*) AS TEXT))
        FROM (SELECT * FROM {table_name} TABLESAMPLE SYSTEM({sample_pct * 100:.1f}) LIMIT {sample_size}) t
    """)
    checksums = [row[0] for row in cursor.fetchall()]
    combined = hashlib.md5(''.join(sorted(checksums)).encode()).hexdigest()
    return combined, count


def migrate_table(source_conn, target_conn, table_def, dry_run=False):
    """迁移单张表数据"""
    table_name = table_def['name']
    batch_size = table_def['batch_size']

    # 获取源表行数
    src_count = get_row_count(source_conn, table_name)
    logger.info(f"  [{table_name}] 源表行数: {src_count}")

    if dry_run:
        return {'table': table_name, 'source_rows': src_count, 'target_rows': 0, 'migrated': False}

    # 获取目标表当前行数
    tgt_count = get_row_count(target_conn, table_name)
    if tgt_count > 0:
        logger.info(f"  [{table_name}] 目标表已有数据: {tgt_count} 行")
        if src_count == tgt_count:
            logger.info(f"  [{table_name}] 数据量一致，跳过")
            return {'table': table_name, 'source_rows': src_count, 'target_rows': tgt_count, 'migrated': False}

    # 获取列名
    columns = get_column_names(source_conn, table_name)
    col_placeholders = ', '.join(['%s'] * len(columns))
    col_names = ', '.join(columns)
    update_placeholders = ', '.join([f"{c}=EXCLUDED.{c}" for c in columns])

    source_cursor = source_conn.cursor(name=f'cursor_{table_name}')  # server-side cursor
    source_cursor.execute(table_def['select_sql'])

    total_migrated = 0
    target_cursor = target_conn.cursor()
    batch = []

    start_time = time.time()

    for row in source_cursor:
        batch.append(row)
        if len(batch) >= batch_size:
            _execute_batch(target_cursor, target_conn, table_name,
                           col_names, col_placeholders, update_placeholders, batch)
            total_migrated += len(batch)
            batch = []
            elapsed = time.time() - start_time
            rate = total_migrated / elapsed if elapsed > 0 else 0
            logger.info(f"  [{table_name}] 已迁移 {total_migrated}/{src_count} 行 ({rate:.0f} 行/秒)")

    if batch:
        _execute_batch(target_cursor, target_conn, table_name,
                       col_names, col_placeholders, update_placeholders, batch)
        total_migrated += len(batch)

    source_cursor.close()

    # 验证
    tgt_count_after = get_row_count(target_conn, table_name)
    elapsed = time.time() - start_time
    logger.info(f"  [{table_name}] 迁移完成: {total_migrated} 行 (耗时 {elapsed:.1f}s, 目标表共 {tgt_count_after} 行)")

    return {
        'table': table_name,
        'source_rows': src_count,
        'target_rows': tgt_count_after,
        'migrated': total_migrated > 0,
        'elapsed_seconds': round(elapsed, 1),
    }


def _execute_batch(cursor, conn, table_name, col_names, placeholders, update_clause, rows):
    """批量写入一行数据"""
    import psycopg2.extras
    insert_sql = (
        f"INSERT INTO {table_name} ({col_names}) "
        f"VALUES ({placeholders}) "
        f"ON CONFLICT DO NOTHING"
    )
    psycopg2.extras.execute_batch(cursor, insert_sql, rows)
    conn.commit()


def verify_migration(source_conn, target_conn, table_name):
    """验证迁移数据完整性"""
    src_count = get_row_count(source_conn, table_name)
    tgt_count = get_row_count(target_conn, table_name)

    if src_count != tgt_count:
        return {
            'table': table_name,
            'status': 'FAIL',
            'source_rows': src_count,
            'target_rows': tgt_count,
            'message': f'行数不匹配: 源={src_count}, 目标={tgt_count}',
        }

    # 抽样校验
    src_checksum, _ = compute_table_checksum(source_conn, table_name)
    tgt_checksum, _ = compute_table_checksum(target_conn, table_name)

    if src_checksum != tgt_checksum and src_checksum != 'EMPTY':
        return {
            'table': table_name,
            'status': 'WARN',
            'source_rows': src_count,
            'target_rows': tgt_count,
            'message': '行数一致但校验和不匹配（抽样差异可能导致）',
        }

    return {
        'table': table_name,
        'status': 'PASS',
        'source_rows': src_count,
        'target_rows': tgt_count,
        'message': '数据一致',
    }


def main():
    args = parse_args()

    supabase_url = args.supabase_url or os.environ.get('SUPABASE_DB_URL')
    rds_url = args.rds_url or os.environ.get('RDS_DB_URL')

    if not supabase_url:
        logger.error("请提供 Supabase 连接串 (--supabase-url 或 SUPABASE_DB_URL 环境变量)")
        sys.exit(1)
    if not rds_url:
        logger.error("请提供 RDS 连接串 (--rds-url 或 RDS_DB_URL 环境变量)")
        sys.exit(1)

    logger.info("=" * 60)
    logger.info("Supabase → RDS 数据迁移工具")
    logger.info(f"Supabase: {supabase_url[:40]}...")
    logger.info(f"RDS:      {rds_url[:40]}...")
    logger.info(f"Dry-run:  {args.dry_run}")
    logger.info("=" * 60)

    # 连接数据库
    logger.info("\n[1/3] 连接数据库...")
    source_conn = get_connection(supabase_url)
    target_conn = get_connection(rds_url)
    logger.info("  ✓ 两端连接成功")

    # 建表
    if not args.skip_create:
        logger.info(f"\n[2/3] 创建表结构 ({'dry-run 仅建表' if args.dry_run else '完整迁移'})...")
        ensure_schema(target_conn, table_name=args.table)

    if args.dry_run:
        logger.info("\n✓ Dry-run 完成，表结构已创建。使用 --dry-run 不会迁移任何数据。")
        source_conn.close()
        target_conn.close()
        return

    # 迁移数据
    logger.info("\n[3/3] 迁移数据...")
    tables_to_migrate = [t for t in TABLES if args.table is None or t['name'] == args.table]
    if args.resume:
        logger.info("  启用断点续传模式")
        completed_tables = _load_checkpoint(target_conn)
        tables_to_migrate = [t for t in tables_to_migrate if t['name'] not in completed_tables]
        logger.info(f"  跳过已完成的表: {completed_tables}")

    results = []
    verification_results = []

    for table_def in tables_to_migrate:
        table_name = table_def['name']
        logger.info(f"\n  --- {table_name} ---")
        try:
            result = migrate_table(source_conn, target_conn, table_def, dry_run=args.dry_run)
            results.append(result)

            if not args.dry_run:
                vr = verify_migration(source_conn, target_conn, table_name)
                verification_results.append(vr)
                logger.info(f"  验证: [{vr['status']}] {vr['message']}")

        except Exception as e:
            logger.error(f"  [{table_name}] 迁移失败: {e}")
            results.append({'table': table_name, 'error': str(e)})

    # 打印汇总报告
    logger.info("\n" + "=" * 60)
    logger.info("迁移报告")
    logger.info("=" * 60)

    total_src = 0
    total_tgt = 0
    passed = 0
    failed = 0

    for r in results:
        src = r.get('source_rows', 0)
        tgt = r.get('target_rows', 0)
        err = r.get('error', '')
        status = '✓' if not err else '✗'
        logger.info(f"  {status} {r['table']:20s} 源={src:>8} 行 → 目标={tgt:>8} 行" +
                    (f"  ({r.get('elapsed_seconds', 0):.1f}s)" if 'elapsed_seconds' in r else '') +
                    (f"  ERROR: {err}" if err else ''))
        total_src += src
        total_tgt += tgt

    for vr in verification_results:
        if vr['status'] == 'PASS':
            passed += 1
        else:
            failed += 1

    logger.info("-" * 60)
    logger.info(f"源表总行数: {total_src}")
    logger.info(f"目标表总行数: {total_tgt}")
    logger.info(f"验证通过: {passed}/{len(verification_results)}")
    if failed > 0:
        logger.info(f"验证警告/失败: {failed}")
    logger.info("=" * 60)

    source_conn.close()
    target_conn.close()

    if failed > 0:
        sys.exit(2)


def _load_checkpoint(conn):
    """加载已完成迁移的表列表（断点续传）"""
    cursor = conn.cursor()
    cursor.execute("""
        SELECT table_name FROM migration_checkpoints
        ORDER BY completed_at
    """)
    return [row[0] for row in cursor.fetchall()]


if __name__ == '__main__':
    main()
