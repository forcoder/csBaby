from typing import Optional
from domain.entities.device import Device
from domain.repositories.device_repo import DeviceRepository
from infrastructure.persistence.database import get_connection


class SqliteDeviceRepository(DeviceRepository):
    def create(self, device: Device) -> Device:
        db = get_connection()
        db.execute(
            "INSERT INTO devices (id, token, name, platform, app_version) VALUES (?, ?, ?, ?, ?)",
            (device.id, device.token, device.name, device.platform, device.app_version),
        )
        db.commit()
        db.close()
        return device

    def get_by_id(self, device_id: str) -> Optional[Device]:
        db = get_connection()
        row = db.execute("SELECT * FROM devices WHERE id = ?", (device_id,)).fetchone()
        db.close()
        if not row:
            return None
        r = dict(row)
        return Device(
            id=r["id"], token=r["token"], name=r["name"],
            platform=r["platform"], app_version=r["app_version"],
        )

    def update_heartbeat(self, device_id: str) -> None:
        db = get_connection()
        db.execute("UPDATE devices SET last_heartbeat = CURRENT_TIMESTAMP WHERE id = ?", (device_id,))
        db.commit()
        db.close()
