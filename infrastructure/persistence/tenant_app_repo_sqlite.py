from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteTenantAppRepository:
    def upsert(self, user_id: str, **fields) -> None:
        db = get_connection()
        defaults = {
            "app_name": "客服小秘",
            "welcome_message": "您好，请问有什么可以帮您？",
            "offline_message": "当前无客服在线，请稍后再试。",
            "auto_reply_enabled": 1, "notification_enabled": 1, "voice_enabled": 0,
            "language": "zh-CN", "session_timeout": 300, "max_queue_size": 50,
            "file_upload_enabled": 1,
        }
        merged = {**defaults, **fields}
        db.execute(
            """INSERT INTO tenant_app_config (user_id, app_name, welcome_message,
               offline_message, auto_reply_enabled, notification_enabled, voice_enabled,
               language, session_timeout, max_queue_size, file_upload_enabled)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(user_id) DO UPDATE SET
                 app_name=excluded.app_name, welcome_message=excluded.welcome_message,
                 offline_message=excluded.offline_message,
                 auto_reply_enabled=excluded.auto_reply_enabled,
                 notification_enabled=excluded.notification_enabled,
                 voice_enabled=excluded.voice_enabled, language=excluded.language,
                 session_timeout=excluded.session_timeout,
                 max_queue_size=excluded.max_queue_size,
                 file_upload_enabled=excluded.file_upload_enabled""",
            (user_id, merged["app_name"], merged["welcome_message"],
             merged["offline_message"], merged["auto_reply_enabled"],
             merged["notification_enabled"], merged["voice_enabled"],
             merged["language"], merged["session_timeout"],
             merged["max_queue_size"], merged["file_upload_enabled"]),
        )
        db.commit()
        SyncWriter(db).push("tenant_app_config", "UPDATE", None, {"user_id": user_id, **merged})
        db.close()