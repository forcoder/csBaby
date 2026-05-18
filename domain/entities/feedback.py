from dataclasses import dataclass
from datetime import datetime


@dataclass
class Feedback:
    id: int = 0
    user_id: str = ""
    reply_history_id: int = 0
    action: str = ""
    modified_text: str = ""
    rating: int = 0
    comment: str = ""
    created_at: datetime = None
