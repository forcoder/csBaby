from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional
import uuid


@dataclass
class Device:
    id: str = ""
    token: str = ""
    name: str = ""
    platform: str = "android"
    app_version: str = ""
    last_heartbeat: Optional[datetime] = None
    created_at: Optional[datetime] = None

    @staticmethod
    def create(name: str = "", platform: str = "android", app_version: str = "") -> "Device":
        return Device(
            id=str(uuid.uuid4()),
            name=name,
            platform=platform,
            app_version=app_version,
        )
