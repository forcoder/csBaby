import os, psycopg2

conn = psycopg2.connect(
    host=os.environ["DB_HOST"],
    port=os.environ["DB_PORT"],
    user=os.environ["DB_USER"],
    password=os.environ["DB_PASSWORD"],
    database=os.environ["DB_NAME"]
)
cur = conn.cursor()

target = "db810b7b-ca17-4069-a7f9-1f6f13e4298e"
old = "41312dd9-ae14-4d64-b8a7-93a78c11178b"

print("=== 分步执行 ===")

# 1. 先更新用户 tenant_id
cur.execute("UPDATE users SET tenant_id = %s WHERE email = 'test@test.com'", (target,))
print("[1] 用户 tenant_id 更新: %d 行" % cur.rowcount)
conn.commit()

# 2. 迁移其他表（跳过 sync_checkpoints 和 backup_records）
tables = ["keyword_rules", "ai_model_configs", "user_style_profiles",
          "app_configs", "scenarios", "reply_history", "message_blacklist"]
for tbl in tables:
    try:
        sql = "UPDATE " + tbl + " SET tenant_id = %s WHERE tenant_id = %s"
        cur.execute(sql, (target, old))
        if cur.rowcount > 0:
            print("[2] 迁移 " + tbl + ": %d 条" % cur.rowcount)
    except Exception as e:
        conn.rollback()
        print("[2] 跳过 " + tbl + ": " + str(e))
conn.commit()

# 3. 处理 sync_checkpoints（可能已存在目标租户的记录）
try:
    cur.execute("SELECT COUNT(*) FROM sync_checkpoints WHERE tenant_id = %s", (target,))
    exists = cur.fetchone()[0]
    if exists > 0:
        cur.execute("DELETE FROM sync_checkpoints WHERE tenant_id = %s", (old,))
        if cur.rowcount > 0:
            print("[3] 删除旧租户 sync_checkpoints: %d 条（目标已存在，跳过迁移）" % cur.rowcount)
    else:
        cur.execute("UPDATE sync_checkpoints SET tenant_id = %s WHERE tenant_id = %s", (target, old))
        if cur.rowcount > 0:
            print("[3] 迁移 sync_checkpoints: %d 条" % cur.rowcount)
except Exception as e:
    print("[3] sync_checkpoints 处理跳过: " + str(e))
conn.commit()

# 4. 处理 backup_records
try:
    cur.execute("UPDATE backup_records SET tenant_id = %s WHERE tenant_id = %s", (target, old))
    if cur.rowcount > 0:
        print("[4] 迁移 backup_records: %d 条" % cur.rowcount)
except Exception as e:
    print("[4] backup_records 跳过: " + str(e))
conn.commit()

# 验证
print()
print("=== 最终验证 ===")
cur.execute("SELECT tenant_id, email, display_name FROM users WHERE email = 'test@test.com'")
r = cur.fetchone()
print("用户 tenant_id:", r[0])
print("用户 email:", r[1])

cur.execute("SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s AND deleted = false", (target,))
print("关键词规则数:", cur.fetchone()[0])

# 检查旧租户还有无残留
cur.execute("SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s AND deleted = false", (old,))
remain = cur.fetchone()[0]
print("旧租户残留规则:", remain)

conn.close()
