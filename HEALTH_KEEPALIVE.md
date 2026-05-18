# Render 健康检测与保活配置

## 概述

此项目已添加：

1. **增强的 `/health` 端点** - 位于 `app.py` 第146-157行
2. **保活脚本** - 位于 `scripts/keepalive.py`
3. **完整测试套件** - 位于 `tests/test_health.py` 和 `tests/test_keepalive.py`

## `/health` 端点功能

### 响应格式（成功时）
```json
{
  "status": "ok",
  "service": "csBaby-api",
  "timestamp": "2026-05-17T09:28:45.123456Z",
  "database": "ok"
}
```

### 响应格式（数据库异常时）
```json
{
  "status": "degraded",
  "service": "csBaby-api",
  "timestamp": "2026-05-17T09:28:45.123456Z",
  "database": "<错误信息>"
}
```

### HTTP 状态码
- `200 OK` - 服务正常，数据库连通
- `503 Service Unavailable` - 数据库连接失败

## 保活脚本使用

### 1. 在 121.43.55.151 上安装

```bash
# 克隆项目
git clone https://github.com/forcoder/csBaby-backend.git
cd csBaby-backend

# 安装 Python 依赖（如有需要）
python -m pip install requests

# 验证脚本可运行
python scripts/keepalive.py
```

### 2. 设置 Cron 任务

```bash
# 编辑 crontab
crontab -e

# 添加以下行：每分钟请求 /health 防止 Render 免费实例休眠
* * * * * /usr/bin/python3 /path/to/csBaby-backend/scripts/keepalive.py >> /tmp/keepalive.log 2>&1
```

### 3. 环境变量配置（可选）

```bash
# 设置 Render URL（默认: https://csbaby-api2.onrender.com）
export RENDER_URL=https://csbaby-api2.onrender.com

# 运行脚本
python scripts/keepalive.py
```

### 4. 日志监控

```bash
# 查看实时日志
tail -f /tmp/keepalive.log

# 检查最近 10 条记录
tail -n 10 /tmp/keepalive.log
```

## 验证部署

### 本地验证
```bash
python -c "
import sys, os
sys.path.insert(0, 'scripts')
import keepalive
print('Health check result:', keepalive.ping_health())
"
```

### 通过 Render 验证
```bash
curl -s https://csbaby-api2.onrender.com/health | python -m json.tool
```

## 测试

运行完整测试套件：
```bash
python -m pytest tests/test_health.py tests/test_keepalive.py -v
# 预期: 13 个测试全部通过
```

## 技术细节

### 健康检查逻辑
1. 获取 UTC 时间戳 (`ISO 8601` 格式)
2. 尝试建立数据库连接并执行 `SELECT 1`
3. 如果数据库连接失败，捕获异常并设置 `status=degraded`
4. 返回 JSON 响应和适当的 HTTP 状态码

### 保活脚本特性
- **超时保护**: 15 秒超时，避免长时间等待
- **详细日志**: 包含状态码、响应体、错误信息
- **环境变量支持**: 可通过 `RENDER_URL` 自定义目标 URL
- **退出码**: 成功返回 0，失败返回 1（适合 cron 监控）

## 故障排查

### 常见问题

1. **Render 502 错误**
   - 原因: 免费实例已休眠
   - 解决方案: 等待几分钟或检查保活是否正常工作

2. **数据库连接失败**
   - 检查 Render 控制台是否有数据库错误
   - 验证 Render 环境变量 `DATABASE_PATH` 设置正确

3. **Cron 未执行**
   - 检查 crontab 语法: `crontab -l`
   - 确认 Python 路径: `which python3`
   - 检查日志文件权限

4. **网络问题**
   - 验证从 121.43.55.151 可以访问 Render URL
   - 使用 `curl -v` 测试基本连通性

### 调试步骤

```bash
# 1. 手动运行脚本
python scripts/keepalive.py

# 2. 查看详细输出
curl -v https://csbaby-api2.onrender.com/health

# 3. 检查 Render 日志
# (通过 Render 控制台查看应用日志)

# 4. 验证 cron 执行
grep CRON /var/log/syslog
```

## 安全注意事项

- 保活脚本只请求公开 `/health` 端点，无需认证
- 不要在保活脚本中暴露敏感信息
- 定期检查日志确保脚本正常运行