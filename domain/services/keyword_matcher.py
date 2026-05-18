import logging
import re
from typing import Dict, List, Any

logger = logging.getLogger(__name__)


class KeywordMatcher:
    @staticmethod
    def match(rules: List[Dict[str, Any]], message: str) -> List[Dict[str, Any]]:
        matched = []
        msg_lower = message.lower()
        for rule in rules:
            keyword = rule["keyword"]
            match_type = rule.get("match_type", "CONTAINS")
            if match_type == "EXACT" and msg_lower == keyword.lower():
                matched.append(rule)
            elif match_type == "STARTS_WITH" and msg_lower.startswith(keyword.lower()):
                matched.append(rule)
            elif match_type == "ENDS_WITH" and msg_lower.endswith(keyword.lower()):
                matched.append(rule)
            elif match_type == "REGEX":
                try:
                    if re.search(keyword, message, re.IGNORECASE | re.DOTALL):
                        matched.append(rule)
                except re.error:
                    logger.warning("Invalid regex pattern in rule %s: %s", rule.get("id"), keyword)
                except Exception as e:
                    logger.error("Regex match error for rule %s: %s", rule.get("id"), e)
            elif match_type == "CONTAINS" and keyword.lower() in msg_lower:
                matched.append(rule)
        return matched

    @staticmethod
    def apply_template(template: str, context: Dict[str, str]) -> str:
        result = template
        if context:
            for key, value in context.items():
                result = result.replace(f"{{{key}}}", str(value))
        return result
