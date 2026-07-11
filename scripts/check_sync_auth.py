"""分析sync server的认证流程"""
import sys
sys.path.insert(0, "/app")

# 读取auth controller
with open("/app/controllers/auth_controller.py", "r") as f:
    content = f.read()

# 找到路由和函数定义
import re

# 打印所有路由
print("=== 路由 ===")
for m in re.finditer(r'@app\.route\([^)]+\)', content):
    line_num = content[:m.start()].count("\n") + 1
    print(f"  L{line_num}: {m.group()}")

# 打印login相关函数
print("\n=== login/register 函数 ===")
for m in re.finditer(r'@app\.route\([^)]+\)\s*\n\s*def \w+\([^)]*\):', content):
    start = m.start()
    end = content.find("\n\n", start)
    if end == -1:
        end = start + 800
    print(content[start:end])
    print("---")