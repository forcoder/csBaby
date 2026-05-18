from abc import ABC, abstractmethod
from typing import List, Optional
from domain.entities.reply_history import ReplyHistory


class HistoryRepository(ABC):
    @abstractmethod
    def create(self, entry: ReplyHistory) -> ReplyHistory:
        pass

    @abstractmethod
    def get_by_device(self, user_id: str, limit: int, offset: int) -> tuple[List[ReplyHistory], int]:
        pass
