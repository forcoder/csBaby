from abc import ABC, abstractmethod
from typing import List, Optional
from domain.entities.keyword_rule import KeywordRule


class RuleRepository(ABC):
    @abstractmethod
    def create(self, rule: KeywordRule) -> KeywordRule:
        pass

    @abstractmethod
    def get_by_device(self, user_id: str) -> List[KeywordRule]:
        pass

    @abstractmethod
    def get_by_id(self, rule_id: int, user_id: str) -> Optional[KeywordRule]:
        pass

    @abstractmethod
    def update(self, rule: KeywordRule) -> KeywordRule:
        pass

    @abstractmethod
    def delete(self, rule_id: int, user_id: str) -> bool:
        pass

    @abstractmethod
    def batch_create(self, rules: List[KeywordRule], user_id: str, mode: str) -> int:
        pass
