"""
Utility functions for parsing different import formats
"""

import json


def parse_json_content(content):
    """
    Parse JSON content into a list of rule dictionaries.
    Supports both array format [{"rule1": ...}] and object format {"rules": [...]}.

    Args:
        content (str): JSON string to parse

    Returns:
        list: List of rule dictionaries, or empty list if parsing fails
    """
    try:
        parsed = json.loads(content)
        if isinstance(parsed, list):
            return parsed
        if isinstance(parsed, dict):
            return parsed.get("rules", [])
        return []
    except (json.JSONDecodeError, TypeError):
        return []


def parse_json_array(json_string):
    """
    Parse JSON array format directly.

    Args:
        json_string (str): JSON array string

    Returns:
        list: Parsed JSON array or empty list on error
    """
    try:
        return json.loads(json_string) if isinstance(json_string, str) else []
    except (json.JSONDecodeError, TypeError):
        return []


def validate_json_rules(rules):
    """
    Validate that rules have required fields.

    Args:
        rules (list): List of rule dictionaries to validate

    Returns:
        bool: True if all rules are valid, False otherwise
    """
    if not isinstance(rules, list):
        return False

    required_fields = ["keyword", "reply_template"]
    for rule in rules:
        if not isinstance(rule, dict):
            return False
        if not any(field in rule for field in required_fields):
            return False

    return True