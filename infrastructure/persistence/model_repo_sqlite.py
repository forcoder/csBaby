from typing import List, Optional
from domain.entities.model_config import ModelConfig
from domain.repositories.model_repo import ModelRepository
from infrastructure.persistence.database import get_connection


class SqliteModelRepository(ModelRepository):
    def create(self, config: ModelConfig) -> ModelConfig:
        db = get_connection()
        if config.is_default:
            db.execute("UPDATE model_configs SET is_default=0 WHERE user_id=?", (config.user_id,))
        cursor = db.execute(
            """INSERT INTO model_configs
            (user_id, name, model_type, model, api_key, api_endpoint, temperature, max_tokens, is_default, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (config.user_id, config.name, config.model_type, config.model,
             config.api_key, config.api_endpoint, config.temperature, config.max_tokens,
             int(config.is_default), int(config.enabled)),
        )
        config.id = cursor.lastrowid
        db.commit()
        db.close()
        return config

    def get_by_device(self, user_id: str) -> List[ModelConfig]:
        db = get_connection()
        rows = db.execute(
            "SELECT * FROM model_configs WHERE user_id=? ORDER BY is_default DESC, id ASC",
            (user_id,),
        ).fetchall()
        db.close()
        return [self._row_to_entity(r) for r in rows]

    def get_default(self, user_id: str) -> Optional[ModelConfig]:
        db = get_connection()
        row = db.execute(
            "SELECT * FROM model_configs WHERE user_id=? AND is_default=1 AND enabled=1 LIMIT 1",
            (user_id,),
        ).fetchone()
        db.close()
        if not row:
            return None
        return self._row_to_entity(row)

    def get_by_id(self, model_id: int, user_id: str) -> Optional[ModelConfig]:
        db = get_connection()
        row = db.execute(
            "SELECT * FROM model_configs WHERE id=? AND user_id=?", (model_id, user_id)
        ).fetchone()
        db.close()
        if not row:
            return None
        return self._row_to_entity(row)

    def update(self, config: ModelConfig) -> ModelConfig:
        db = get_connection()
        if config.is_default:
            db.execute("UPDATE model_configs SET is_default=0 WHERE user_id=?", (config.user_id,))
        db.execute(
            """UPDATE model_configs SET name=?, model_type=?, model=?, api_key=?, api_endpoint=?,
            temperature=?, max_tokens=?, is_default=?, enabled=?, updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND user_id=?""",
            (config.name, config.model_type, config.model, config.api_key, config.api_endpoint,
             config.temperature, config.max_tokens, int(config.is_default), int(config.enabled),
             config.id, config.user_id),
        )
        db.commit()
        db.close()
        return config

    def delete(self, model_id: int, user_id: str) -> bool:
        db = get_connection()
        cursor = db.execute("DELETE FROM model_configs WHERE id=? AND user_id=?", (model_id, user_id))
        db.commit()
        db.close()
        return cursor.rowcount > 0

    @staticmethod
    def _row_to_entity(r) -> ModelConfig:
        return ModelConfig(
            id=r["id"], user_id=r["user_id"], name=r["name"],
            model_type=r["model_type"], model=r["model"], api_key=r["api_key"],
            api_endpoint=r["api_endpoint"], temperature=r["temperature"],
            max_tokens=r["max_tokens"], is_default=bool(r["is_default"]),
            enabled=bool(r["enabled"]),
        )
