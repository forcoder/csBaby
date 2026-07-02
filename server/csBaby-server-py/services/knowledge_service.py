"""知识库规则业务逻辑 (KnowledgeService)。

提供规则 CRUD 操作，每次变更自动更新 sync_version 以触发多端同步。
"""
import sys
import os
import uuid
import logging
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config.database import execute_query, execute_update, execute_batch

logger = logging.getLogger(__name__)


def _now_ms() -> int:
    """返回当前毫秒时间戳"""
    return int(datetime.now().timestamp() * 1000)


def to_rule_dict(row) -> dict:
    """将数据库行转换为规则字典"""
    return {
        'id': str(row[0]),
        'keyword': row[1],
        'matchType': row[2],
        'replyTemplate': row[3],
        'category': row[4],
        'targetType': row[5],
        'targetNamesJson': row[6],
        'priority': row[7],
        'enabled': bool(row[8]) if row[8] is not None else True,
        'createdAt': row[9],
        'updatedAt': row[10],
        'tenantId': row[11],
        'syncVersion': row[12],
        'deleted': bool(row[13]) if row[13] is not None else False,
    }


class KnowledgeService:
    """知识库规则业务服务"""

    def list_rules(self, tenant_id: str, include_deleted: bool = False,
                   page: int = 1, limit: int = 100) -> dict:
        """获取规则列表，按优先级降序排列

        Args:
            tenant_id: 租户ID
            include_deleted: 是否包含已删除的规则
            page: 页码（从1开始）
            limit: 每页条数（最大500）

        Returns:
            dict: { rules: [...], total: int, page: int, limit: int }
        """
        offset = (page - 1) * limit
        limit = min(limit, 500)

        if include_deleted:
            rules = execute_query(
                """SELECT * FROM keyword_rules
                   WHERE tenant_id = %s
                   ORDER BY priority DESC, created_at DESC
                   LIMIT %s OFFSET %s""",
                (tenant_id, limit, offset)
            )
            total_row = execute_query(
                "SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s",
                (tenant_id,), fetch='one'
            )
        else:
            rules = execute_query(
                """SELECT * FROM keyword_rules
                   WHERE tenant_id = %s AND deleted = FALSE
                   ORDER BY priority DESC, created_at DESC
                   LIMIT %s OFFSET %s""",
                (tenant_id, limit, offset)
            )
            total_row = execute_query(
                "SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s AND deleted = FALSE",
                (tenant_id,), fetch='one'
            )

        total = total_row[0] if total_row else 0
        return {
            'rules': [to_rule_dict(r) for r in rules],
            'total': total,
            'page': page,
            'limit': limit,
        }

    def get_rule(self, tenant_id: str, rule_id: str) -> dict | None:
        """获取单条规则"""
        row = execute_query(
            "SELECT * FROM keyword_rules WHERE id = %s AND tenant_id = %s",
            (rule_id, tenant_id), fetch='one'
        )
        if not row:
            return None
        return to_rule_dict(row)

    def create_rule(self, tenant_id: str, data: dict) -> dict:
        """创建新规则

        Args:
            tenant_id: 租户ID
            data: {
                keyword: str (必填)
                matchType: str (默认 CONTAINS)
                replyTemplate: str (必填)
                category: str (可选)
                targetType: str (默认 ALL)
                targetNamesJson: str (默认 [])
                priority: int (默认 0)
            }

        Returns:
            dict: 创建的规则对象

        Raises:
            ValueError: 参数校验失败
        """
        keyword = (data.get('keyword') or '').strip()
        reply_template = (data.get('replyTemplate') or '').strip()

        if not keyword:
            raise ValueError('关键词不能为空')
        if not reply_template:
            raise ValueError('回复模板不能为空')

        rule_id = str(uuid.uuid4())
        now = _now_ms()

        execute_update(
            """INSERT INTO keyword_rules
               (id, keyword, match_type, reply_template, category,
                target_type, target_names_json, priority, enabled,
                created_at, updated_at, tenant_id, sync_version, deleted)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
            (
                rule_id,
                keyword,
                data.get('matchType', 'CONTAINS'),
                reply_template,
                data.get('category', ''),
                data.get('targetType', 'ALL'),
                data.get('targetNamesJson', '[]'),
                data.get('priority', 0),
                True,  # enabled
                now,   # created_at
                now,   # updated_at
                tenant_id,
                now,   # sync_version — 触发其他终端同步
                False, # deleted
            )
        )

        logger.info(f"create_rule: tenant={tenant_id}, rule_id={rule_id}, keyword={keyword}")
        return self.get_rule(tenant_id, rule_id)

    def update_rule(self, tenant_id: str, rule_id: str, data: dict) -> dict | None:
        """更新规则

        Args:
            tenant_id: 租户ID
            rule_id: 规则ID
            data: 可更新字段 { keyword, matchType, replyTemplate, category,
                              targetType, targetNamesJson, priority, enabled }

        Returns:
            dict | None: 更新后的规则，不存在返回 None
        """
        existing = self.get_rule(tenant_id, rule_id)
        if not existing:
            return None

        now = _now_ms()
        update_fields = []
        update_values = []

        field_map = {
            'keyword': 'keyword',
            'matchType': 'match_type',
            'replyTemplate': 'reply_template',
            'category': 'category',
            'targetType': 'target_type',
            'targetNamesJson': 'target_names_json',
            'priority': 'priority',
            'enabled': 'enabled',
        }

        for api_key, db_col in field_map.items():
            if api_key in data:
                update_fields.append(f"{db_col} = %s")
                update_values.append(data[api_key])

        if not update_fields:
            return existing

        # Always update sync_version and updated_at
        update_fields.append("sync_version = %s")
        update_values.append(now)
        update_fields.append("updated_at = %s")
        update_values.append(now)

        update_values.extend([rule_id, tenant_id])

        execute_update(
            f"""UPDATE keyword_rules
                SET {', '.join(update_fields)}
                WHERE id = %s AND tenant_id = %s""",
            tuple(update_values)
        )

        logger.info(f"update_rule: tenant={tenant_id}, rule_id={rule_id}, fields={list(data.keys())}")
        return self.get_rule(tenant_id, rule_id)

    def delete_rule(self, tenant_id: str, rule_id: str) -> bool:
        """软删除规则

        Returns:
            bool: 是否删除成功
        """
        existing = self.get_rule(tenant_id, rule_id)
        if not existing:
            return False

        now = _now_ms()
        execute_update(
            """UPDATE keyword_rules
               SET deleted = TRUE, sync_version = %s, updated_at = %s
               WHERE id = %s AND tenant_id = %s""",
            (now, now, rule_id, tenant_id)
        )

        logger.info(f"delete_rule: tenant={tenant_id}, rule_id={rule_id}")
        return True

    def search_rules(self, tenant_id: str, keyword: str = '',
                     page: int = 1, limit: int = 100) -> dict:
        """搜索规则（按关键词模糊匹配）

        Args:
            tenant_id: 租户ID
            keyword: 搜索关键词
            page: 页码
            limit: 每页条数

        Returns:
            dict: { rules: [...], total: int }
        """
        offset = (page - 1) * limit
        limit = min(limit, 500)

        if keyword:
            pattern = f"%{keyword}%"
            rules = execute_query(
                """SELECT * FROM keyword_rules
                   WHERE tenant_id = %s AND deleted = FALSE
                   AND (keyword ILIKE %s OR reply_template ILIKE %s OR category ILIKE %s)
                   ORDER BY priority DESC, created_at DESC
                   LIMIT %s OFFSET %s""",
                (tenant_id, pattern, pattern, pattern, limit, offset)
            )
            total_row = execute_query(
                """SELECT COUNT(*) FROM keyword_rules
                   WHERE tenant_id = %s AND deleted = FALSE
                   AND (keyword ILIKE %s OR reply_template ILIKE %s OR category ILIKE %s)""",
                (tenant_id, pattern, pattern, pattern), fetch='one'
            )
        else:
            rules = execute_query(
                """SELECT * FROM keyword_rules
                   WHERE tenant_id = %s AND deleted = FALSE
                   ORDER BY priority DESC, created_at DESC
                   LIMIT %s OFFSET %s""",
                (tenant_id, limit, offset)
            )
            total_row = execute_query(
                "SELECT COUNT(*) FROM keyword_rules WHERE tenant_id = %s AND deleted = FALSE",
                (tenant_id,), fetch='one'
            )

        total = total_row[0] if total_row else 0
        return {
            'rules': [to_rule_dict(r) for r in rules],
            'total': total,
            'page': page,
            'limit': limit,
        }

    def batch_create(self, tenant_id: str, rules: list[dict]) -> dict:
        """批量创建规则

        Args:
            tenant_id: 租户ID
            rules: 规则列表

        Returns:
            dict: { created: int, errors: [...] }
        """
        now = _now_ms()
        statements = []
        created = 0
        errors = []

        for i, rule_data in enumerate(rules):
            keyword = (rule_data.get('keyword') or '').strip()
            reply_template = (rule_data.get('replyTemplate') or '').strip()

            if not keyword or not reply_template:
                errors.append({'index': i, 'error': '关键词或回复模板为空'})
                continue

            rule_id = str(uuid.uuid4())
            statements.append((
                """INSERT INTO keyword_rules
                   (id, keyword, match_type, reply_template, category,
                    target_type, target_names_json, priority, enabled,
                    created_at, updated_at, tenant_id, sync_version, deleted)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    rule_id, keyword,
                    rule_data.get('matchType', 'CONTAINS'),
                    reply_template, rule_data.get('category', ''),
                    rule_data.get('targetType', 'ALL'),
                    rule_data.get('targetNamesJson', '[]'),
                    rule_data.get('priority', 0), True,
                    now, now, tenant_id, now, False,
                )
            ))
            created += 1

        if statements:
            execute_batch(statements)

        logger.info(f"batch_create: tenant={tenant_id}, created={created}, errors={len(errors)}")
        return {'created': created, 'errors': errors}
