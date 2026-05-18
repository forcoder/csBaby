from abc import ABC, abstractmethod
from typing import List, Optional
from domain.entities.model_config import ModelConfig


class ModelRepository(ABC):
    @abstractmethod
    def create(self, config: ModelConfig) -> ModelConfig:
        pass

    @abstractmethod
    def get_by_device(self, user_id: str) -> List[ModelConfig]:
        pass

    @abstractmethod
    def get_default(self, user_id: str) -> Optional[ModelConfig]:
        pass

    @abstractmethod
    def get_by_id(self, model_id: int, user_id: str) -> Optional[ModelConfig]:
        pass

    @abstractmethod
    def update(self, config: ModelConfig) -> ModelConfig:
        pass

    @abstractmethod
    def delete(self, model_id: int, user_id: str) -> bool:
        pass
