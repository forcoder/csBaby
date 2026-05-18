"""
csBaby Render 保活脚本
======================
在 121.43.55.151 上通过 cron 每分钟执行一次，
请求 Render 部署的 /health 端点，防止免费实例休眠。

用法:
  1. 直接运行:  python scripts/keepalive.py
  2. cron 设置:  * * * * * /usr/bin/python3 /path/to/scripts/keepalive.py >> /tmp/keepalive.log 2>&1
"""

import urllib.request
import urllib.error
import logging
import sys
import os

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("keepalive")

RENDER_URL = os.environ.get("RENDER_URL", "https://csbaby-api2.onrender.com")
HEALTH_ENDPOINT = f"{RENDER_URL}/health"
TIMEOUT_SECONDS = 15


def ping_health() -> bool:
    """请求 Render /health 端点，返回是否成功。"""
    try:
        req = urllib.request.Request(HEALTH_ENDPOINT, method="GET")
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            body = resp.read().decode("utf-8")
            status = resp.status
            logger.info("health check status=%d body=%s", status, body)
            return status == 200
    except urllib.error.URLError as exc:
        logger.error("health check failed: %s", exc)
        return False
    except Exception as exc:
        logger.error("unexpected error: %s", exc)
        return False


def main() -> int:
    success = ping_health()
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
