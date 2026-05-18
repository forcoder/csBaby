"""Tests for admin utility functions: parse_json_content, parse_json_array, validate_json_rules."""
import pytest
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from utils import parse_json_content, parse_json_array, validate_json_rules


class TestParseJsonContent:
    def test_parse_json_array(self):
        content = '[{"keyword": "你好", "reply_template": "您好"}]'
        result = parse_json_content(content)
        assert len(result) == 1
        assert result[0]["keyword"] == "你好"

    def test_parse_json_object_with_rules_key(self):
        content = '{"rules": [{"keyword": "test", "reply_template": "reply"}]}'
        result = parse_json_content(content)
        assert len(result) == 1
        assert result[0]["keyword"] == "test"

    def test_parse_json_empty_array(self):
        result = parse_json_content("[]")
        assert result == []

    def test_parse_json_empty_object(self):
        result = parse_json_content("{}")
        assert result == []

    def test_parse_json_invalid(self):
        result = parse_json_content("not valid json")
        assert result == []

    def test_parse_json_empty_string(self):
        result = parse_json_content("")
        assert result == []

    def test_parse_json_nested_rules(self):
        content = '{"rules": [{"keyword": "k1", "reply_template": "r1"}, {"keyword": "k2", "reply_template": "r2"}]}'
        result = parse_json_content(content)
        assert len(result) == 2

    def test_parse_json_primitive_returns_empty(self):
        result = parse_json_content('"string"')
        assert result == []

    def test_parse_json_number_returns_empty(self):
        result = parse_json_content("42")
        assert result == []

    def test_parse_json_null_returns_empty(self):
        result = parse_json_content("null")
        assert result == []


class TestParseJsonArray:
    def test_valid_array(self):
        result = parse_json_array('[{"keyword": "test"}]')
        assert len(result) == 1

    def test_empty_array(self):
        result = parse_json_array("[]")
        assert result == []

    def test_invalid_json(self):
        result = parse_json_array("invalid")
        assert result == []

    def test_non_string_input(self):
        result = parse_json_array(None)
        assert result == []

    def test_non_string_input_dict(self):
        result = parse_json_array({"key": "value"})
        assert result == []


class TestValidateJsonRules:
    def test_valid_rules(self):
        rules = [{"keyword": "test", "reply_template": "reply"}]
        assert validate_json_rules(rules) is True

    def test_empty_list(self):
        assert validate_json_rules([]) is True

    def test_rule_with_only_keyword(self):
        rules = [{"keyword": "test"}]
        assert validate_json_rules(rules) is True

    def test_rule_with_only_reply_template(self):
        rules = [{"reply_template": "reply"}]
        assert validate_json_rules(rules) is True

    def test_rule_missing_required_fields(self):
        rules = [{"category": "test"}]
        assert validate_json_rules(rules) is False

    def test_rules_not_a_list(self):
        assert validate_json_rules("not a list") is False

    def test_rules_dict_not_list(self):
        assert validate_json_rules({"keyword": "test"}) is False

    def test_mixed_valid_invalid_rules(self):
        rules = [{"keyword": "test"}, {"no_required": "field"}]
        assert validate_json_rules(rules) is False

    def test_rule_is_not_dict(self):
        rules = ["string_rule"]
        assert validate_json_rules(rules) is False

    def test_multiple_valid_rules(self):
        rules = [
            {"keyword": "k1", "reply_template": "r1"},
            {"keyword": "k2", "reply_template": "r2"},
        ]
        assert validate_json_rules(rules) is True
