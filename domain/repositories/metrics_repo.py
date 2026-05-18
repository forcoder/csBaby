from abc import ABC, abstractmethod
from typing import List
from domain.entities.optimization_metrics import OptimizationMetrics


class MetricsRepository(ABC):
    @abstractmethod
    def get_by_device_and_date_range(self, user_id: str, days: int) -> List[OptimizationMetrics]:
        pass

    @abstractmethod
    def increment_metric(self, user_id: str, action: str) -> None:
        pass
