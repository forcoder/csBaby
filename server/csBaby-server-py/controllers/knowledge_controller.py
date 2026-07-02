"""知识库规则 CRUD API (Flask Blueprint)。

端点:
  GET    /api/knowledge/rules          - 获取规则列表
  GET    /api/knowledge/rules/search   - 搜索规则
  POST   /api/knowledge/rules          - 创建规则
  GET    /api/knowledge/rules/<id>     - 获取单条规则
  PUT    /api/knowledge/rules/<id>     - 更新规则
  DELETE /api/knowledge/rules/<id>     - 删除规则
  POST   /api/knowledge/rules/batch    - 批量创建规则

这些 CRUD 操作自动更新 sync_version，通过现有 sync 机制实现多端同步。
"""
import os
import jwt as pyjwt
from flask import Blueprint, request, jsonify, g
from functools import wraps
from services.knowledge_service import KnowledgeService

knowledge_bp = Blueprint('knowledge', __name__)
service = KnowledgeService()

JWT_SECRET = os.getenv('JWT_SECRET', 'default-secret-change-me')
JWT_ALGORITHM = 'HS256'


def _verify_token(token):
    """验证 JWT access token，返回 payload 或 None"""
    try:
        payload = pyjwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        if payload.get('type') != 'access':
            return None
        return payload
    except (pyjwt.ExpiredSignatureError, pyjwt.InvalidTokenError):
        return None


def require_knowledge_auth(f):
    """认证装饰器 — 验证 Bearer token，设置 g.tenant_id / g.user_id"""
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get('Authorization', '')
        if not auth_header.startswith('Bearer '):
            return jsonify({'code': 401, 'message': '缺少认证令牌'}), 401

        token = auth_header[7:]
        payload = _verify_token(token)
        if not payload:
            return jsonify({'code': 401, 'message': '令牌无效或已过期'}), 401

        g.user_id = payload['user_id']
        g.tenant_id = payload.get('tenant_id') or payload.get('user_id', '')
        return f(*args, **kwargs)
    return decorated


@knowledge_bp.route('/api/knowledge/rules', methods=['GET'])
@require_knowledge_auth
def list_rules():
    """获取规则列表"""
    try:
        page = int(request.args.get('page', 1))
        limit = int(request.args.get('limit', 100))
        include_deleted = request.args.get('include_deleted', 'false').lower() == 'true'

        result = service.list_rules(
            tenant_id=g.tenant_id,
            include_deleted=include_deleted,
            page=page,
            limit=limit,
        )
        return jsonify({'code': 0, 'message': '成功', 'data': result})
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e)}), 500


@knowledge_bp.route('/api/knowledge/rules/search', methods=['GET'])
@require_knowledge_auth
def search_rules():
    """搜索规则"""
    try:
        keyword = request.args.get('keyword', '')
        page = int(request.args.get('page', 1))
        limit = int(request.args.get('limit', 100))

        result = service.search_rules(
            tenant_id=g.tenant_id,
            keyword=keyword,
            page=page,
            limit=limit,
        )
        return jsonify({'code': 0, 'message': '成功', 'data': result})
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e)}), 500


@knowledge_bp.route('/api/knowledge/rules', methods=['POST'])
@require_knowledge_auth
def create_rule():
    """创建规则"""
    try:
        data = request.get_json() or {}
        rule = service.create_rule(g.tenant_id, data)
        return jsonify({'code': 0, 'message': '创建成功', 'data': rule}), 201
    except ValueError as e:
        return jsonify({'code': 400, 'message': str(e)}), 400
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e)}), 500


@knowledge_bp.route('/api/knowledge/rules/<rule_id>', methods=['GET'])
@require_knowledge_auth
def get_rule(rule_id):
    """获取单条规则"""
    try:
        rule = service.get_rule(g.tenant_id, rule_id)
        if not rule:
            return jsonify({'code': 404, 'message': '规则不存在'}), 404
        if rule['deleted']:
            return jsonify({'code': 404, 'message': '规则不存在'}), 404
        return jsonify({'code': 0, 'message': '成功', 'data': rule})
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e)}), 500


@knowledge_bp.route('/api/knowledge/rules/<rule_id>', methods=['PUT'])
@require_knowledge_auth
def update_rule(rule_id):
    """更新规则"""
    try:
        data = request.get_json() or {}
        rule = service.update_rule(g.tenant_id, rule_id, data)
        if not rule:
            return jsonify({'code': 404, 'message': '规则不存在'}), 404
        return jsonify({'code': 0, 'message': '更新成功', 'data': rule})
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e)}), 500


@knowledge_bp.route('/api/knowledge/rules/<rule_id>', methods=['DELETE'])
@require_knowledge_auth
def delete_rule(rule_id):
    """删除规则（软删除）"""
    try:
        deleted = service.delete_rule(g.tenant_id, rule_id)
        if not deleted:
            return jsonify({'code': 404, 'message': '规则不存在'}), 404
        return jsonify({'code': 0, 'message': '删除成功'})
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e)}), 500


@knowledge_bp.route('/api/knowledge/rules/batch', methods=['POST'])
@require_knowledge_auth
def batch_create_rules():
    """批量创建规则"""
    try:
        data = request.get_json() or {}
        rules = data.get('rules', [])
        if not rules:
            return jsonify({'code': 400, 'message': '规则列表不能为空'}), 400

        result = service.batch_create(g.tenant_id, rules)
        return jsonify({'code': 0, 'message': '批量创建完成', 'data': result})
    except Exception as e:
        return jsonify({'code': 500, 'message': str(e)}), 500
