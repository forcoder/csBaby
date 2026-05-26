"""csBaby Server Python - Web.py Application Entry Point"""
import web
from controllers.health_controller import HealthCheck

urls = (
    '/', 'Index',
    '/health', 'HealthCheck',
    '/auth/register', 'Register',
)

app = web.application(urls, globals())


class Index:
    """首页端点"""

    def GET(self):
        """返回服务基本信息"""
        return {'message': 'csBaby Sync Server Python'}


class Register:
    """用户注册端点"""

    def POST(self):
        """处理用户注册请求"""
        return {'message': 'Register endpoint'}


if __name__ == "__main__":
    app.run()
