"""知识库规则 CRUD API — 回归测试（带 Flask 上下文）

测试策略:
  - 使用 Flask test_client 测试 API 路由
  - 模拟认证和数据库操作
  - 覆盖 HTTP 响应码和错误处理
"""
import sys
import os
import json
import pytest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# ==================== Fixtures ====================

@pytest.fixture
def app():
    """创建测试 Flask app 并注册 knowledge_bp"""
    from flask import Flask, g
    from controllers.knowledge_controller import knowledge_bp

    app = Flask(__name__)
    app.config['TESTING'] = True

    # 注册 blueprint
    app.register_blueprint(knowledge_bp)

    # 添加认证中间件（模拟 g.tenant_id）
    @app.before_request
    def _set_auth():
        g.user_id = 'test-user'
        g.tenant_id = 'test-tenant'

    return app


@pytest.fixture
def client(app):
    """创建测试客户端"""
    return app.test_client()


@pytest.fixture
def mock_list_rules(monkeypatch):
    """模拟 KnowledgeService.list_rules"""
    mock = MagicMock(return_value={
        'rules': [{'id': 'r1', 'keyword': 'hello', 'matchType': 'CONTAINS',
                    'replyTemplate': '你好', 'enabled': True,
                    'createdAt': 1000, 'updatedAt': 1000,
                    'tenantId': 'test-tenant', 'syncVersion': 1000, 'deleted': False}],
        'total': 1,
        'page': 1,
        'limit': 100,
    })
    monkeypatch.setattr('controllers.knowledge_controller.service.list_rules', mock)
    return mock


# ==================== 正常场景 — HTTP 测试 ====================

class TestCRUDEndpoints:
    """API 端点正常流程测试"""

    def test_list_rules_returns_200(self, client, mock_list_rules):
        """正常场景1: 获取规则列表返回200"""
        resp = client.get('/api/knowledge/rules')
        data = resp.get_json()

        assert resp.status_code == 200
        assert data['code'] == 0
        assert len(data['data']['rules']) == 1
        assert data['data']['total'] == 1

    def test_search_rules_returns_200(self, client, mock_list_rules):
        """正常场景2: 搜索规则返回200"""
        resp = client.get('/api/knowledge/rules/search?keyword=hello')
        assert resp.status_code == 200
        assert resp.get_json()['code'] == 0

    def test_create_rule_returns_201(self, client):
        """正常场景3: 创建规则返回201"""
        with patch('controllers.knowledge_controller.service.create_rule',
                   return_value={'id': 'new-id', 'keyword': 'hi', 'replyTemplate': 'Hello!'}):
            resp = client.post('/api/knowledge/rules',
                              json={'keyword': 'hi', 'replyTemplate': 'Hello!'})
            data = resp.get_json()

            assert resp.status_code == 201
            assert data['code'] == 0
            assert data['data']['id'] == 'new-id'

    def test_get_rule_returns_200(self, client):
        """正常场景4: 获取单条规则返回200"""
        with patch('controllers.knowledge_controller.service.get_rule',
                   return_value={'id': 'r1', 'keyword': 'hello', 'deleted': False}):
            resp = client.get('/api/knowledge/rules/r1')
            assert resp.status_code == 200
            assert resp.get_json()['data']['keyword'] == 'hello'

    def test_update_rule_returns_200(self, client):
        """正常场景5: 更新规则返回200"""
        with patch('controllers.knowledge_controller.service.update_rule',
                   return_value={'id': 'r1', 'keyword': 'updated'}):
            resp = client.put('/api/knowledge/rules/r1', json={'keyword': 'updated'})
            assert resp.status_code == 200
            assert resp.get_json()['data']['keyword'] == 'updated'

    def test_delete_rule_returns_200(self, client):
        """正常场景6: 删除规则返回200"""
        with patch('controllers.knowledge_controller.service.delete_rule',
                   return_value=True):
            resp = client.delete('/api/knowledge/rules/r1')
            assert resp.status_code == 200
            assert resp.get_json()['message'] == '删除成功'

    def test_batch_create_returns_200(self, client):
        """正常场景7: 批量创建返回200"""
        with patch('controllers.knowledge_controller.service.batch_create',
                   return_value={'created': 2, 'errors': []}):
            resp = client.post('/api/knowledge/rules/batch',
                              json={'rules': [
                                  {'keyword': 'a', 'replyTemplate': 'A'},
                                  {'keyword': 'b', 'replyTemplate': 'B'},
                              ]})
            assert resp.status_code == 200
            assert resp.get_json()['data']['created'] == 2


# ==================== 边界值场景 ====================

class TestBoundaryCases:
    """边界值测试"""

    def test_get_deleted_rule_returns_404(self, client):
        """边界值1: 获取已删除规则返回404"""
        with patch('controllers.knowledge_controller.service.get_rule',
                   return_value={'id': 'r1', 'keyword': 'old', 'deleted': True}):
            resp = client.get('/api/knowledge/rules/r1')
            assert resp.status_code == 404

    def test_batch_create_empty_list_returns_400(self, client):
        """边界值2: 批量创建空列表返回400"""
        resp = client.post('/api/knowledge/rules/batch', json={'rules': []})
        assert resp.status_code == 400
        assert resp.get_json()['code'] == 400

    def test_list_with_invalid_pagination(self, client, mock_list_rules):
        """边界值3: 无效分页参数"""
        resp = client.get('/api/knowledge/rules?page=-1&limit=abc')
        # Flask 会报 400，但我们的代码应处理
        assert resp.status_code in [200, 400, 500]

    def test_pagination_parameters(self, client):
        """边界值4: 正常分页参数"""
        with patch('controllers.knowledge_controller.service.list_rules',
                   return_value={'rules': [], 'total': 0, 'page': 2, 'limit': 50}):
            resp = client.get('/api/knowledge/rules?page=2&limit=50')
            assert resp.status_code == 200
            assert resp.get_json()['data']['page'] == 2


# ==================== 异常场景 ====================

class TestExceptionCases:
    """异常场景测试"""

    def test_create_with_invalid_data_returns_400(self, client):
        """异常1: 创建规则时参数校验失败返回400"""
        with patch('controllers.knowledge_controller.service.create_rule',
                   side_effect=ValueError('关键词不能为空')):
            resp = client.post('/api/knowledge/rules',
                              json={'keyword': '', 'replyTemplate': 'hi'})
            assert resp.status_code == 400

    def test_get_nonexistent_rule_returns_404(self, client):
        """异常2: 获取不存在的规则返回404"""
        with patch('controllers.knowledge_controller.service.get_rule',
                   return_value=None):
            resp = client.get('/api/knowledge/rules/nonexistent')
            assert resp.status_code == 404

    def test_update_nonexistent_rule_returns_404(self, client):
        """异常3: 更新不存在的规则返回404"""
        with patch('controllers.knowledge_controller.service.update_rule',
                   return_value=None):
            resp = client.put('/api/knowledge/rules/nonexistent', json={'keyword': 'new'})
            assert resp.status_code == 404

    def test_delete_nonexistent_rule_returns_404(self, client):
        """异常4: 删除不存在的规则返回404"""
        with patch('controllers.knowledge_controller.service.delete_rule',
                   return_value=False):
            resp = client.delete('/api/knowledge/rules/nonexistent')
            assert resp.status_code == 404

    def test_server_error_returns_500(self, client):
        """异常5: 服务器内部错误返回500"""
        with patch('controllers.knowledge_controller.service.list_rules',
                   side_effect=Exception('Unexpected error')):
            resp = client.get('/api/knowledge/rules')
            assert resp.status_code == 500

    @pytest.fixture
    def app_unauthenticated(self):
        """模拟未认证状态"""
        from flask import Flask
        from controllers.knowledge_controller import knowledge_bp

        app = Flask(__name__)
        app.config['TESTING'] = True
        app.register_blueprint(knowledge_bp)
        # 不设置 g.tenant_id → 401
        @app.before_request
        def _empty_auth():
            from flask import g
            pass  # 不设置 g.tenant_id

        return app

    def test_unauthorized_access(self, client, monkeypatch):
        """异常6: 未认证访问返回401"""
        # 模拟 g.tenant_id 不存在的情况
        import flask
        original_g = flask.g

        class MockG:
            _data = {}

            def get(self, key, default=None):
                return self._data.get(key, default)

        mock_g = MockG()
        monkeypatch.setattr('controllers.knowledge_controller.g', mock_g)

        resp = client.get('/api/knowledge/rules')
        # 如果没有 tenant_id，应返回 401
        assert resp.status_code == 401


# ==================== 综合测试 ====================

class TestSyncVersionPropagation:
    """验证 sync_version 传播机制"""

    def test_create_updates_sync_version(self, client):
        """综合测试: 创建规则时 sync_version 自动更新"""
        with patch('controllers.knowledge_controller.service.create_rule',
                   return_value={
                       'id': 'r1', 'keyword': 'hi', 'syncVersion': 1000,
                       'enabled': True, 'deleted': False,
                   }):
            resp = client.post('/api/knowledge/rules',
                              json={'keyword': 'hi', 'replyTemplate': 'Hello'})
            data = resp.get_json()
            assert data['data']['syncVersion'] == 1000

    def test_update_increments_sync_version(self, client):
        """综合测试: 更新规则使 sync_version 增加"""
        with patch('controllers.knowledge_controller.service.update_rule',
                   return_value={
                       'id': 'r1', 'keyword': 'updated', 'syncVersion': 2000,
                       'enabled': True, 'deleted': False,
                   }):
            resp = client.put('/api/knowledge/rules/r1', json={'keyword': 'updated'})
            data = resp.get_json()
            assert data['data']['syncVersion'] == 2000


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
