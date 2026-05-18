from dataclasses import dataclass


@dataclass
class OptimizationMetrics:
    id: int = 0
    user_id: str = ""
    date: str = ""
    total_generated: int = 0
    total_accepted: int = 0
    total_modified: int = 0
    total_rejected: int = 0
    avg_confidence: float = 0.0
    avg_response_time_ms: int = 0
