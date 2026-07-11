import pymysql, os
DB_HOST=os.environ["DB_HOST"]; DB_PORT=int(os.environ["DB_PORT"])
DB_USER=os.environ["DB_USER"]; DB_PASSWORD=os.environ["DB_PASSWORD"]
DB_NAME=os.environ["DB_NAME"]
c = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASSWORD, database=DB_NAME)
cur = c.cursor()
cur.execute("SELECT DATABASE()"); print("DB:", cur.fetchone())
cur.execute("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' ORDER BY ORDINAL_POSITION")
print("COLUMNS:", [r[0] for r in cur.fetchall()])
try:
    cur.execute("SELECT account FROM users LIMIT 1"); print("account OK:", cur.fetchone())
except Exception as e: print("account FAIL:", repr(e))
c.close()
print("DONE")
