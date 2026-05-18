from abc import ABC, abstractmethod
from typing import Optional
from domain.entities.device import Device


class DeviceRepository(ABC):
    @abstractmethod
    def create(self, device: Device) -> Device:
        pass

    @abstractmethod
    def get_by_id(self, device_id: str) -> Optional[Device]:
        pass

    @abstractmethod
    def update_heartbeat(self, device_id: str) -> None:
        pass
