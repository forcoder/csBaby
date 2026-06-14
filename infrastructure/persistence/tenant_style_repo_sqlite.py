from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteTenantStyleRepository:
    def upsert(self, user_id: str, **fields) -> None:
        db = get_connection()
        defaults = {
            "theme": "light", "primary_color": "#1976D2", "accent_color": "#FF4081",
            "font_size": "medium", "bubble_style": "rounded",
            "avatar_enabled": 1, "show_timestamp": 1, "send_sound": 1,
            "custom_css": "",
        }
        merged = {**defaults, **fields}
        db.execute(
            """INSERT INTO tenant_style_config (user_id, theme, primary_color, accent_color,
               font_size, bubble_style, avatar_enabled, show_timestamp, send_sound, custom_css)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(user_id) DO UPDATE SET
                 theme=excluded.theme, primary_color=excluded.primary_color,
                 accent_color=excluded.accent_color, font_size=excluded.font_size,
                 bubble_style=excluded.bubble_style,
                 avatar_enabled=excluded.avatar_enabled,
                 show_timestamp=excluded.show_timestamp,
                 send_sound=excluded.send_sound,
                 custom_css=excluded.custom_css""",
            (user_id, merged["theme"], merged["primary_color"], merged["accent_color"],
             merged["font_size"], merged["bubble_style"], merged["avatar_enabled"],
             merged["show_timestamp"], merged["send_sound"], merged["custom_css"]),
        )
        db.commit()
        SyncWriter(db).push("tenant_style_config", "UPDATE", None, {"user_id": user_id, **merged})
        db.close()