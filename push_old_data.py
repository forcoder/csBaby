"""推送旧手机数据到 15088670554 账号"""
import csv, json, urllib.request, ssl, sys

TENANT = "30c30b28-89d0-4db8-bed4-7666b065355e"
BATCH_SIZE = 50

# 读取 CSV
rules = []
with open("phone_keyword_rules.csv", "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        rules.append({
            "id": row["id"],
            "keyword": row["keyword"],
            "matchType": row["matchType"],
            "replyTemplate": row["replyTemplate"],
            "category": row.get("category", ""),
            "targetType": row.get("targetType", "ALL"),
            "targetNamesJson": row.get("targetNamesJson", "[]"),
            "priority": int(row.get("priority", 0)),
            "enabled": row.get("enabled", "1") == "1",
            "createdAt": int(row.get("createdAt", "0")),
            "updatedAt": int(row.get("updatedAt", "0")),
            "deleted": row.get("deleted", "0") == "1"
        })

total = len(rules)
print(f"共 {total} 条规则，分 {(total + BATCH_SIZE - 1) // BATCH_SIZE} 批推送")

# 获取 token
import subprocess
result = subprocess.run([
    "ssh", "-p", "2222", "root@121.43.55.151",
    "python3 -c \"import jwt,time;payload={\\\"user_id\\\":\\\"30c30b28-89d0-4db8-bed4-7666b065355e\\\",\\\"tenant_id\\\":\\\"30c30b28-89d0-4db8-bed4-7666b065355e\\\",\\\"type\\\":\\\"access\\\",\\\"exp\\\":int(time.time())+86400,\\\"iat\\\":int(time.time())};tok=jwt.encode(payload,\\\"dd53372859d994b821a9d38b546fa6fe17e6e762de1aa4242d4ae1a5334cd94c\\\",algorithm=\\\"HS256\\\");print(tok.decode() if isinstance(tok,bytes) else tok)\""
], capture_output=True, text=True)
TOKEN = result.stdout.strip()
print(f"Token: {TOKEN[:30]}...")

# 分批推送
ctx = ssl._create_unverified_context()
for i in range(0, total, BATCH_SIZE):
    batch = rules[i:i+BATCH_SIZE]
    payload = json.dumps({
        "tenantId": TENANT,
        "keywordRules": batch,
        "deletedIds": {},
        "baseVersion": 0
    }).encode("utf-8")
    
    req = urllib.request.Request(
        "http://sync.agentai0.com/sync/push",
        data=payload,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json"
        },
        method="POST"
    )
    try:
        resp = urllib.request.urlopen(req, timeout=60)
        result = json.loads(resp.read())
        stats = result.get("data", {}).get("stats", {})
        print(f"  批次 {i//BATCH_SIZE + 1}: {len(batch)} 条 -> code={result.get('code')}", end="")
        if stats:
            print(f" (inserted={stats.get('inserted',0)}, updated={stats.get('updated',0)})")
        else:
            print()
    except Exception as e:
        print(f"  批次 {i//BATCH_SIZE + 1}: 失败 - {e}")

print("\n全部推送完成")
