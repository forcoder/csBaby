#!/usr/bin/env python3
"""
RDS 重复数据清理脚本 — 清理因 pushAllLocalChanges 推送导致的重复规则。

问题背景:
  restoreAuthState 中调用了 pushAllLocalChanges，把本地 519 条规则全部推送到服务端。
  服务端 push_changes 使用 (tenant_id, keyword_hash) 作为 UPSERT 冲突键，
  当 keyword+replyTemplate 组合不同时，会创建新规则而非更新已有规则，
  导致 keyword_rules 表中出现大量重复数据。

执行:
  cd server/csBaby-server-py
  python scripts/rds_cleanup.py

安全模式:
  python scripts/rds_cleanup.py --dry-run   # 只查找不删除
"""

import os
import sys
import logging
import argparse

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from config.database import execute_query, execute_update, IS_MYSQL

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S',
)
logger = logging.getLogger('rds_cleanup')


def find_duplicate_rules():
    """查找 keyword_rules 表中重复的规则（相同 keyword + tenant_id，且未删除）"""
    sql = """
        SELECT a.id, a.keyword, a.tenant_id, a.sync_version, a.updated_at,
               b.id as dup_id, b.sync_version as dup_sync_version, b.updated_at as dup_updated_at
        FROM keyword_rules a
        JOIN keyword_rules b ON a.keyword = b.keyword AND a.tenant_id = b.tenant_id
            AND a.deleted = 0 AND b.deleted = 0
            AND a.id < b.id
        ORDER BY a.tenant_id, a.keyword, a.id
    """
    return execute_query(sql)


def clean_duplicate_rules(dry_run=False):
    """清理重复规则，保留 sync_version 最高的那条"""
    logger.info("=" * 60)
    logger.info("RDS 重复数据清理工具")
    logger.info(f"模式: {'DRY RUN (只查找不删除)' if dry_run else '实际执行'}")
    logger.info("=" * 60)

    # 查找所有重复规则
    duplicates = find_duplicate_rules()
    logger.info(f"找到 {len(duplicates)} 组重复规则")

    if not duplicates:
        logger.info("没有发现重复规则，数据库已清洁")
        return

    # 按 tenant_id + keyword 分组，找出每组应该保留的规则
    seen = {}  # (tenant_id, keyword) -> best_id
    to_delete = set()  # 需要删除的 id 集合

    for row in duplicates:
        key = (row[2], row[1])  # (tenant_id, keyword)
        id_a, sync_a, updated_a = row[0], row[3], row[4]
        id_b, sync_b, updated_b = row[5], row[6], row[7]

        if key not in seen:
            # 比较 a 和 b，保留 sync_version 更高的
            kept_id = id_a if (sync_a, updated_a) >= (sync_b, updated_b) else id_b
            deleted_id = id_b if kept_id == id_a else id_a
            seen[key] = kept_id
            to_delete.add(deleted_id)
        else:
            best_id = seen[key]
            # 比较当前行与 best_id，保留更好的
            for id_val, sync_val, updated_val in [(id_a, sync_a, updated_a), (id_b, sync_b, updated_b)]:
                if id_val == best_id:
                    continue
                if id_val not in to_delete:
                    # 比较 best 和当前 id
                    best_sync = sync_a if best_id == id_a else sync_b
                    best_updated = updated_a if best_id == id_a else updated_b
                    if (sync_val, updated_val) > (best_sync, best_updated):
                        to_delete.add(best_id)
                        seen[key] = id_val
                    else:
                        to_delete.add(id_val)

    logger.info(f"需要删除的规则数: {len(to_delete)}")

    if not to_delete:
        logger.info("没有需要删除的规则")
        return

    # 显示将被删除的规则
    logger.info("待删除的规则 ID:")
    for rid in sorted(to_delete):
        logger.info(f"  - {rid}")

    if dry_run:
        logger.info("[DRY RUN] 未执行实际删除操作")
        # 显示清理后的预计规则数
        total = execute_query(
            "SELECT COUNT(*) FROM keyword_rules WHERE deleted=0", fetch='one'
        )
        logger.info(f"当前有效规则数: {total[0]}")
        logger.info(f"预计清理后规则数: {total[0] - len(to_delete)}")
        return

    # 执行删除（软删除）
    confirm = input(f"确认删除 {len(to_delete)} 条重复规则? (yes/no): ")
    if confirm.lower() != 'yes':
        logger.info("已取消")
        return

    deleted_count = 0
    for rid in to_delete:
        affected = execute_update(
            "UPDATE keyword_rules SET deleted=1, sync_version=%s WHERE id=%s",
            (int(datetime.now().timestamp() * 1000), rid)
        )
        if affected > 0:
            deleted_count += 1

    logger.info(f"实际删除 {deleted_count} 条重复规则")

    # 显示清理后的统计
    total = execute_query(
        "SELECT COUNT(*) FROM keyword_rules WHERE deleted=0", fetch='one'
    )
    logger.info(f"清理后有效规则数: {total[0]}")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='清理 RDS 中重复的 keyword_rules')
    parser.add_argument('--dry-run', action='store_true', help='Dry run 模式，只查找不删除')
    args = parser.parse_args()
    clean_duplicate_rules(dry_run=args.dry_run)