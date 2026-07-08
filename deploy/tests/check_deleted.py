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

# 检查全部规则（含已删除）
cur.execute("SELECT deleted, COUNT(*) FROM keyword_rules WHERE tenant_id = %s GROUP BY deleted", (target,))
print("=== 全部规则状态 ===")
for r in cur.fetchall():
    print("  deleted=%s: %d 条" % (r[0], r[1]))

# 检查软删除的规则
cur.execute("SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s AND deleted = true", (target,))
soft_deleted = cur.fetchone()[0]
print("  软删除(deleted=true): %d 条" % soft_deleted)

# 检查活跃规则
cur.execute("SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s AND deleted = false", (target,))
active = cur.fetchone()[0]
print("  活跃(deleted=false): %d 条" % active)

print()
if soft_deleted > 0:
    print("规则是软删除状态，可以直接恢复！")
else:
    print("没有软删除记录，规则可能被物理删除了。")

# 检查最近的操作（如果有 sync_checkpoints 等）
cur.execute("SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s", (target,))
total = cur.fetchone()[0]
print("  表中该租户规则总数:", total)

conn.close()
