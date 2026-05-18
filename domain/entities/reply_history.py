from dataclasses import dataclass
from datetime import datetime


@dataclass
class ReplyHistory:
    id: int = 0
    user_id: str = ""
    original_message: str = ""
    reply_content: str = ""
    source: str = "ai"
    model_used: str = ""
    confidence: float = 0.0
    response_time_ms: int = 0
    platform: str = ""
    customer_name: str = ""
    house_name: str = ""
    created_at: datetime = None
