"""迁移SQLite数据到MySQL RDS (api_表)"""
import json
import pymysql
import sqlite3
import sys

# MySQL连接
mysql_conn = pymysql.connect(
    host="r8371qiaozhou.mysql.aliyun.com",
    port=3306,
    user="qiaozhou",
    password="Rds@2026",
    database="r2346qiaozhou",
    charset="utf8mb4",
    connect_timeout=10,
    ssl_disabled=True,
    autocommit=True
)
mysql = mysql_conn.cursor()

# SQLite连接
sqlite = sqlite3.connect("/data/csbaby.db")
sqlite.row_factory = sqlite3.Row
cur = sqlite.cursor()

# 表映射: (sqlite表名, mysql表名, 列映射, 特殊处理)
TABLES = [
    ("users", "api_users", None, None),
    ("user_devices", "api_user_devices", None, None),
    ("devices", "api_devices", None, None),
    ("keyword_rules", "api_keyword_rules", None, None),
    ("model_configs", "api_model_configs", None, None),
    ("reply_history", "api_reply_history", None, None),
    ("feedback", "api_feedback", None, None),
    ("optimization_metrics", "api_optimization_metrics", None, None),
    ("blacklist", "api_blacklist", None, None),
    ("agent_status", "api_agent_status", None, None),
    ("agent_skills", "api_agent_skills", None, None),
    ("routing_config", "api_routing_config", None, None),
    ("sessions", "api_sessions", None, None),
    ("tenant_style_config", "api_tenant_style_config", None, None),
    ("tenant_app_config", "api_tenant_app_config", None, None),
    ("admin_accounts", "api_admin_accounts", None, None),
    ("admin_sessions", "api_admin_sessions", None, None),
    ("audit_log", "api_audit_log", None, None),
    ("sync_outbox", "api_sync_outbox", None, None),
    ("sync_outbox_dead", "api_sync_outbox_dead", None, None),
]

total_rows = 0

for sqlite_table, mysql_table, col_map, _ in TABLES:
    try:
        # 获取SQLite数据
        cur.execute(f"SELECT * FROM \"{sqlite_table}\"")
        rows = cur.fetchall()
        if not rows:
            print(f"  {sqlite_table} -> {mysql_table}: 0 行 (跳过)")
            continue
        
        # 获取列名
        col_names = [desc[0] for desc in cur.description]
        
        # 清空MySQL表
        mysql.execute(f"DELETE FROM `{mysql_table}`")
        
        # 构造INSERT语句
        placeholders = ", ".join(["%s"] * len(col_names))
        cols = ", ".join([f"`{c}`" for c in col_names])
        insert_sql = f"INSERT INTO `{mysql_table}` ({cols}) VALUES ({placeholders})"
        
        # 批量插入
        batch_size = 100
        batch = []
        for row in rows:
            values = []
            for i, col in enumerate(col_names):
                val = row[i]
                # 只截断datetime类型字段，不截断hash/salt等长字符串
                if val is not None and isinstance(val, str) and len(val) > 19:
                    # 只截断看起来像datetime的字符串
                    if col in ('created_at', 'updated_at', 'registered_at', 'last_heartbeat', 
                               'expires_at', 'next_retry_at', 'closed_at', 'moved_at'):
                        val = val[:19]
                values.append(val)
            batch.append(values)
            
            if len(batch) >= batch_size:
                mysql.executemany(insert_sql, batch)
                batch = []
        
        if batch:
            mysql.executemany(insert_sql, batch)
        
        print(f"  {sqlite_table} -> {mysql_table}: {len(rows)} 行 ✓")
        total_rows += len(rows)
        
    except Exception as e:
        print(f"  {sqlite_table} -> {mysql_table}: 失败 - {e}")

print(f"\n=== 迁移完成！共 {total_rows} 行数据 ===")

# 验证数据
print("\n=== 验证数据 ===")
for sqlite_table, mysql_table, _, _ in TABLES:
    try:
        cur.execute(f"SELECT COUNT(*) FROM \"{sqlite_table}\"")
        sqlite_cnt = cur.fetchone()[0]
        mysql.execute(f"SELECT COUNT(*) FROM `{mysql_table}`")
        mysql_cnt = mysql.fetchone()[0]
        status = "✓" if sqlite_cnt == mysql_cnt else "✗"
        print(f"  {status} {sqlite_table}: sqlite={sqlite_cnt} mysql={mysql_cnt}")
    except Exception as e:
        print(f"  ? {sqlite_table}: {e}")

sqlite.close()
mysql_conn.close()