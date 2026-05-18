from dataclasses import dataclass
from datetime import datetime


@dataclass
class ModelConfig:
    id: int = 0
    user_id: str = ""
    name: str = ""
    model_type: str = "OPENAI"
    model: str = ""
    api_key: str = ""
    api_endpoint: str = ""
    temperature: float = 0.7
    max_tokens: int = 2000
    is_default: bool = False
    enabled: bool = True
    created_at: datetime = None
    updated_at: datetime = None
