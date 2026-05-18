from dataclasses import dataclass, field
from typing import List
from datetime import datetime


@dataclass
class KeywordRule:
    id: int = 0
    user_id: str = ""
    keyword: str = ""
    match_type: str = "CONTAINS"
    reply_template: str = ""
    category: str = ""
    target_type: str = "ALL"
    target_names: List[str] = field(default_factory=list)
    priority: int = 0
    enabled: bool = True
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)
