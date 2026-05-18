from typing import List
from datetime import datetime
from domain.entities.optimization_metrics import OptimizationMetrics
from domain.repositories.metrics_repo import MetricsRepository
from infrastructure.persistence.database import get_connection


class SqliteMetricsRepository(MetricsRepository):
    def get_by_device_and_date_range(self, user_id: str, days: int) -> List[OptimizationMetrics]:
        db = get_connection()
        rows = db.execute(
            "SELECT * FROM optimization_metrics WHERE user_id=? AND date >= date('now', ?) ORDER BY date DESC",
            (user_id, f"-{days} days"),
        ).fetchall()
        db.close()
        return [OptimizationMetrics(
            id=r["id"], user_id=r["user_id"], date=r["date"],
            total_generated=r["total_generated"], total_accepted=r["total_accepted"],
            total_modified=r["total_modified"], total_rejected=r["total_rejected"],
        ) for r in rows]

    def increment_metric(self, user_id: str, action: str) -> None:
        today = datetime.now().strftime("%Y-%m-%d")
        field_map = {
            "generated": "total_generated", "accepted": "total_accepted",
            "modified": "total_modified", "rejected": "total_rejected",
        }
        field = field_map.get(action)
        if not field:
            return
        db = get_connection()
        db.execute(
            f"""INSERT INTO optimization_metrics (user_id, date, {field})
                VALUES (?, ?, 1)
                ON CONFLICT(user_id, date) DO UPDATE SET {field} = {field} + 1""",
            (user_id, today),
        )
        db.commit()
        db.close()
