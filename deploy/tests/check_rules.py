import os, psycopg2

conn = psycopg2.connect(
    host=os.environ["DB_HOST"],
    port=os.environ["DB_PORT"],
    user=os.environ["DB_USER"],
    password=os.environ["DB_PASSWORD"],
    database=os.environ["DB_NAME"]
)
cur = conn.cursor()

print("=== 所有规则按tenant_id分组 ===")
cur.execute("SELECT tenant_id, COUNT(*) as cnt FROM keyword_rules WHERE deleted = false GROUP BY tenant_id ORDER BY cnt DESC")
rows = cur.fetchall()
print("%-40s %5s" % ("tenant_id", "规则数"))
print("-"*50)
for r in rows:
    print("%-40s %5d" % (r[0], r[1]))

print()
print("=== 关联用户 ===")
cur.execute("SELECT tenant_id, email, display_name FROM users WHERE tenant_id IN (SELECT DISTINCT tenant_id FROM keyword_rules WHERE deleted = false)")
user_map = {r[0]: r for r in cur.fetchall()}
for r in rows:
    u = user_map.get(r[0])
    if u:
        print("  %s | email=%-30s | name=%s | 规则数=%d" % (r[0][:20], u[1] or "", u[2] or "", r[1]))
    else:
        print("  %s | (无对应用户) | 规则数=%d" % (r[0][:20], r[1]))

print()
print("=== 所有用户列表 ===")
cur.execute("SELECT tenant_id, email, display_name FROM users ORDER BY created_at DESC")
for r in cur.fetchall():
    print("  %-40s %-30s %s" % (r[0][:20]+"...", r[1] or "", r[2] or ""))

conn.close()
