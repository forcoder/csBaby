from infrastructure.persistence.database import get_connection
import pymysql

# connect using env directly
import os
DB_HOST = os.environ["DB_HOST"]
DB_PORT = int(os.environ["DB_PORT"])
DB_USER = os.environ["DB_USER"]
DB_PASSWORD = os.environ["DB_PASSWORD"]
DB_NAME = os.environ["DB_NAME"]

raw = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER,
                      password=DB_PASSWORD, database=DB_NAME)
cur = raw.cursor()
cur.execute("SELECT DATABASE()")
print("DB:", cur.fetchone())
cur.execute("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' ORDER BY ORDINAL_POSITION")
print("COLUMNS:", [r[0] for r in cur.fetchall()])
try:
    cur.execute("SELECT account FROM users LIMIT 1")
    print("account query OK:", cur.fetchone())
except Exception as e:
    print("account query FAIL:", e)
cur.close()
print("DONE")
