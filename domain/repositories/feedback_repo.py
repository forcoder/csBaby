from abc import ABC, abstractmethod
from typing import List
from domain.entities.feedback import Feedback


class FeedbackRepository(ABC):
    @abstractmethod
    def create(self, feedback: Feedback) -> Feedback:
        pass

    @abstractmethod
    def get_by_device(self, user_id: str, limit: int, offset: int) -> List[Feedback]:
        pass
