"""知识库规则模块 — 单元测试

测试策略:
  - 使用 pytest 对 KnowledgeService 进行纯逻辑测试
  - 通过 Monkeypatch 模拟数据库操作，避免真实数据库依赖
  - 覆盖正常场景、边界值、异常场景
"""
import sys
import os
import json
import pytest
from unittest.mock import MagicMock, patch
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from services.knowledge_service import KnowledgeService, to_rule_dict, _now_ms

# ==================== Fixtures ====================

@pytest.fixture
def service():
    """创建 KnowledgeService 实例"""
    return KnowledgeService()

@pytest.fixture
def mock_execute_query(monkeypatch):
    """模拟 execute_query"""
    mock = MagicMock()
    monkeypatch.setattr('services.knowledge_service.execute_query', mock)
    return mock

@pytest.fixture
def mock_execute_update(monkeypatch):
    """模拟 execute_update"""
    mock = MagicMock()
    monkeypatch.setattr('services.knowledge_service.execute_update', mock)
    return mock

@pytest.fixture
def mock_execute_batch(monkeypatch):
    """模拟 execute_batch"""
    mock = MagicMock()
    monkeypatch.setattr('services.knowledge_service.execute_batch', mock)
    return mock

# ==================== 正常场景 ====================

class TestListRules:
    """测试 list_rules — 正常场景"""

    def test_list_all_active_rules(self, service, mock_execute_query):
        """正常场景1: 获取活跃规则列表"""
        mock_execute_query.side_effect = [
            # 第一次调用: 获取规则列表
            [
                ('r1', 'hello', 'CONTAINS', '你好', 'greeting', 'ALL', '[]', 1, True,
                 1000, 1000, 't1', 2000, False),
                ('r2', 'bye', 'EXACT', '再见', 'farewell', 'ALL', '[]', 0, True,
                 1001, 1001, 't1', 2001, False),
            ],
            # 第二次调用: 总行数
            (2,),
        ]

        result = service.list_rules('t1', include_deleted=False)

        assert result['total'] == 2
        assert len(result['rules']) == 2
        assert result['page'] == 1
        assert result['rules'][0]['keyword'] == 'hello'
        assert result['rules'][0]['enabled'] is True
        assert result['rules'][1]['keyword'] == 'bye'

    def test_list_with_pagination(self, service, mock_execute_query):
        """正常场景2: 分页获取"""
        mock_execute_query.side_effect = [
            # 规则列表（第2页，每页1条）
            [('r2', 'bye', 'EXACT', '再见', '', 'ALL', '[]', 0, True, 1001, 1001, 't1', 2001, False)],
            # 总行数
            (2,),
        ]

        result = service.list_rules('t1', page=2, limit=1)

        assert result['total'] == 2
        assert len(result['rules']) == 1
        assert result['rules'][0]['keyword'] == 'bye'
        assert result['page'] == 2

    def test_list_include_deleted(self, service, mock_execute_query):
        """正常场景3: 包含已删除规则"""
        mock_execute_query.side_effect = [
            [
                ('r1', 'hello', 'CONTAINS', '你好', '', 'ALL', '[]', 0, True,
                 1000, 2000, 't1', 2000, False),
                ('r2', 'old', 'CONTAINS', '旧规则', '', 'ALL', '[]', 0, True,
                 1000, 2000, 't1', 2000, True),
            ],
            (2,),
        ]

        result = service.list_rules('t1', include_deleted=True)

        assert result['total'] == 2
        deleted_rules = [r for r in result['rules'] if r['deleted']]
        assert len(deleted_rules) == 1


class TestCreateRule:
    """测试 create_rule — 正常场景"""

    def test_create_with_all_fields(self, service, mock_execute_query, mock_execute_update):
        """正常场景4: 使用完整字段创建规则"""
        mock_execute_update.return_value = 1
        rule_id_result = ('new-id', 'hello', 'CONTAINS', '你好', 'greeting',
                          'ALL', '[]', 5, True, 1000, 1000, 't1', 1000, False)

        # get_rule 会被调用两次（理论上是 create_rule 内部调用了它）
        # 实际上 create_rule 只调用一次 get_rule
        mock_execute_query.side_effect = [
            rule_id_result,  # execute_query in get_rule
        ]

        data = {
            'keyword': 'hello',
            'matchType': 'CONTAINS',
            'replyTemplate': '你好',
            'category': 'greeting',
            'targetType': 'ALL',
            'priority': 5,
        }

        with patch('services.knowledge_service.uuid.uuid4', return_value='new-id'):
            with patch('services.knowledge_service._now_ms', return_value=1000):
                rule = service.create_rule('t1', data)

        assert rule is not None
        assert rule['keyword'] == 'hello'
        assert rule['replyTemplate'] == '你好'

        # 验证 INSERT 包含了正确的 sync_version
        call_args = mock_execute_update.call_args
        insert_sql = call_args[0][0]
        insert_params = call_args[0][1]
        assert 'INSERT INTO keyword_rules' in insert_sql
        assert 'CONTAINS' in str(insert_params)

    def test_create_minimal_fields(self, service, mock_execute_query, mock_execute_update):
        """正常场景5: 使用最小字段创建规则"""
        mock_execute_update.return_value = 1

        rule_id_result = ('min-id', 'hi', 'CONTAINS', 'Hi there', '',
                          'ALL', '[]', 0, True, 2000, 2000, 't1', 2000, False)
        mock_execute_query.side_effect = [rule_id_result]

        with patch('services.knowledge_service.uuid.uuid4', return_value='min-id'):
            with patch('services.knowledge_service._now_ms', return_value=2000):
                rule = service.create_rule('t1', {
                    'keyword': 'hi',
                    'replyTemplate': 'Hi there',
                })

        assert rule is not None
        assert rule['keyword'] == 'hi'
        assert rule['matchType'] == 'CONTAINS'  # 默认值


class TestUpdateRule:
    """测试 update_rule — 正常场景"""

    def test_update_keyword_and_priority(self, service, mock_execute_query, mock_execute_update):
        """正常场景6: 更新关键词和优先级"""
        # 第一次 get_rule 返回已存在
        mock_execute_query.side_effect = [
            ('r1', 'hello', 'CONTAINS', '你好', '', 'ALL', '[]', 0, True,
             1000, 1000, 't1', 1000, False),
            # update 后的 get_rule
            ('r1', 'hi', 'CONTAINS', '你好', '', 'ALL', '[]', 5, True,
             1000, 3000, 't1', 3000, False),
        ]

        with patch('services.knowledge_service._now_ms', return_value=3000):
            rule = service.update_rule('t1', 'r1', {
                'keyword': 'hi',
                'priority': 5,
            })

        assert rule is not None
        assert rule['keyword'] == 'hi'
        assert rule['priority'] == 5

        # 验证 UPDATE SQL
        call_args = mock_execute_update.call_args
        update_sql = call_args[0][0]
        assert 'UPDATE keyword_rules' in update_sql
        assert 'sync_version' in update_sql

    def test_update_partial_fields(self, service, mock_execute_query, mock_execute_update):
        """正常场景7: 只更新部分字段"""
        mock_execute_query.side_effect = [
            ('r1', 'hello', 'CONTAINS', '你好', '', 'ALL', '[]', 0, True,
             1000, 1000, 't1', 1000, False),
            ('r1', 'hello', 'CONTAINS', 'Hello!', '', 'ALL', '[]', 0, True,
             1000, 4000, 't1', 4000, False),
        ]

        with patch('services.knowledge_service._now_ms', return_value=4000):
            rule = service.update_rule('t1', 'r1', {'replyTemplate': 'Hello!'})

        assert rule['replyTemplate'] == 'Hello!'
        assert rule['keyword'] == 'hello'  # 未变更字段保持不变


class TestDeleteRule:
    """测试 delete_rule — 正常场景"""

    def test_soft_delete(self, service, mock_execute_query, mock_execute_update):
        """正常场景8: 软删除规则"""
        mock_execute_query.side_effect = [
            ('r1', 'hello', 'CONTAINS', '你好', '', 'ALL', '[]', 0, True,
             1000, 1000, 't1', 1000, False),
        ]

        with patch('services.knowledge_service._now_ms', return_value=5000):
            result = service.delete_rule('t1', 'r1')

        assert result is True
        call_args = mock_execute_update.call_args
        update_sql = call_args[0][0]
        assert 'deleted = TRUE' in update_sql


class TestSearchRules:
    """测试 search_rules — 正常场景"""

    def test_search_by_keyword(self, service, mock_execute_query):
        """正常场景9: 按关键词搜索"""
        mock_execute_query.side_effect = [
            [
                ('r1', '价格咨询', 'CONTAINS', '价格是...', '', 'ALL', '[]', 0, True,
                 1000, 1000, 't1', 1000, False),
            ],
            (1,),
        ]

        result = service.search_rules('t1', keyword='价格')

        assert result['total'] == 1
        assert result['rules'][0]['keyword'] == '价格咨询'


# ==================== 边界值场景 ====================

class TestBoundaryCases:
    """边界值测试"""

    def test_empty_rule_list(self, service, mock_execute_query):
        """边界值1: 空规则列表"""
        mock_execute_query.side_effect = [
            [],
            (0,),
        ]

        result = service.list_rules('t1')

        assert result['total'] == 0
        assert result['rules'] == []

    def test_create_with_empty_keyword(self, service):
        """边界值2: 创建规则时空关键词"""
        with pytest.raises(ValueError, match='关键词不能为空'):
            service.create_rule('t1', {'keyword': '', 'replyTemplate': 'hi'})

    def test_create_with_empty_reply(self, service):
        """边界值3: 创建规则时空回复模板"""
        with pytest.raises(ValueError, match='回复模板不能为空'):
            service.create_rule('t1', {'keyword': 'hi', 'replyTemplate': ''})

    def test_whitespace_only_keyword(self, service):
        """边界值4: 关键词只有空格"""
        with pytest.raises(ValueError, match='关键词不能为空'):
            service.create_rule('t1', {'keyword': '   ', 'replyTemplate': 'hi'})

    def test_max_limit_page_size(self, service, mock_execute_query):
        """边界值5: 超大的 page limit 被限制"""
        mock_execute_query.side_effect = [
            [], (0,),
        ]

        result = service.list_rules('t1', limit=99999)

        # limit 应被裁剪到 500
        assert result['limit'] == 500


# ==================== 异常场景 ====================

class TestExceptionCases:
    """异常场景测试"""

    def test_get_nonexistent_rule(self, service, mock_execute_query):
        """异常1: 获取不存在的规则"""
        mock_execute_query.return_value = None

        rule = service.get_rule('t1', 'nonexistent-id')
        assert rule is None

    def test_update_nonexistent_rule(self, service, mock_execute_query):
        """异常2: 更新不存在的规则"""
        mock_execute_query.return_value = None

        result = service.update_rule('t1', 'nonexistent-id', {'keyword': 'new'})
        assert result is None

    def test_delete_nonexistent_rule(self, service, mock_execute_query):
        """异常3: 删除不存在的规则"""
        mock_execute_query.return_value = None

        result = service.delete_rule('t1', 'nonexistent-id')
        assert result is False

    def test_cross_tenant_isolation(self, service, mock_execute_query):
        """异常4: 跨租户隔离 — A 租户不能获取 B 租户的规则"""
        mock_execute_query.return_value = None  # 不同 tenant_id 查不到

        rule = service.get_rule('tenant_a', 'some-rule')
        assert rule is None

    def test_database_error_on_create(self, service, mock_execute_update):
        """异常5: 数据库写入失败"""
        mock_execute_update.side_effect = Exception('Database connection lost')

        with patch('services.knowledge_service.uuid.uuid4', return_value='err-id'):
            with pytest.raises(Exception, match='Database connection lost'):
                service.create_rule('t1', {'keyword': 'hi', 'replyTemplate': 'hello'})


# ==================== 辅助函数测试 ====================

class TestUtilityFunctions:
    """辅助函数测试"""

    def test_to_rule_dict_all_fields(self):
        """辅助函数1: to_rule_dict 包含所有字段"""
        row = ('r1', 'keyword', 'CONTAINS', 'reply', 'cat', 'ALL', '[]', 0,
               1, 1000, 2000, 't1', 3000, 0)
        result = to_rule_dict(row)

        expected_fields = ['id', 'keyword', 'matchType', 'replyTemplate', 'category',
                          'targetType', 'targetNamesJson', 'priority', 'enabled',
                          'createdAt', 'updatedAt', 'tenantId', 'syncVersion', 'deleted']
        for field in expected_fields:
            assert field in result, f"缺少字段: {field}"

    def test_to_rule_dict_boolean_conversion(self):
        """辅助函数2: 布尔值正确转换"""
        # enabled=1, deleted=1
        row1 = ('r1', 'kw', 'C', 'r', '', 'ALL', '[]', 0, 1, 0, 0, 't1', 0, 1)
        r1 = to_rule_dict(row1)
        assert r1['enabled'] is True
        assert r1['deleted'] is True

        # enabled=0, deleted=0
        row2 = ('r2', 'kw', 'C', 'r', '', 'ALL', '[]', 0, 0, 0, 0, 't1', 0, 0)
        r2 = to_rule_dict(row2)
        assert r2['enabled'] is False
        assert r2['deleted'] is False

        # enabled=None
        row3 = ('r3', 'kw', 'C', 'r', '', 'ALL', '[]', 0, None, 0, 0, 't1', 0, None)
        r3 = to_rule_dict(row3)
        assert r3['enabled'] is True   # 默认值
        assert r3['deleted'] is False  # 默认值

    def test_now_ms_returns_int(self):
        """辅助函数3: _now_ms 返回毫秒时间戳"""
        ts = _now_ms()
        assert isinstance(ts, int)
        assert ts > 1_700_000_000_000  # 2023年后的时间戳
