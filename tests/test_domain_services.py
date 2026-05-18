"""Tests for domain services: AuthService, KeywordMatcher, AIService."""
import time
import pytest
from domain.services.auth_service import AuthService
from domain.services.keyword_matcher import KeywordMatcher


class TestAuthService:
    def setup_method(self):
        self.service = AuthService("test-secret-key", jwt_expire_days=30)

    def test_generate_token_returns_valid_jwt(self):
        token = self.service.generate_token("device-123")
        assert token.count(".") == 2  # header.payload.signature
        parts = token.split(".")
        assert len(parts) == 3
        assert all(len(p) > 0 for p in parts)

    def test_verify_token_returns_device_id(self):
        token = self.service.generate_token("device-456")
        result = self.service.verify_token(token)
        assert result == "device-456"

    def test_verify_token_rejects_tampered_signature(self):
        token = self.service.generate_token("device-789")
        parts = token.split(".")
        # Tamper with signature
        tampered = f"{parts[0]}.{parts[1]}.AAAA"
        assert self.service.verify_token(tampered) is None

    def test_verify_token_rejects_expired_token(self):
        # Create service with negative expiry to generate already-expired token
        expired_service = AuthService("test-secret", jwt_expire_days=-1)
        token = expired_service.generate_token("device-000")
        assert self.service.verify_token(token) is None

    def test_verify_token_rejects_malformed_token(self):
        assert self.service.verify_token("not-a-jwt") is None
        assert self.service.verify_token("a.b") is None
        assert self.service.verify_token("") is None
        assert self.service.verify_token("a.b.c.d") is None

    def test_verify_token_rejects_wrong_secret(self):
        token = self.service.generate_token("device-111")
        other_service = AuthService("different-secret")
        assert other_service.verify_token(token) is None

    def test_different_devices_get_different_tokens(self):
        t1 = self.service.generate_token("device-a")
        t2 = self.service.generate_token("device-b")
        assert t1 != t2


class TestKeywordMatcher:
    def test_exact_match(self):
        rules = [{"keyword": "hello", "match_type": "EXACT", "reply_template": "Hi!"}]
        result = KeywordMatcher.match(rules, "hello")
        assert len(result) == 1
        assert result[0]["reply_template"] == "Hi!"

    def test_exact_no_match_different_case(self):
        rules = [{"keyword": "hello", "match_type": "EXACT", "reply_template": "Hi!"}]
        result = KeywordMatcher.match(rules, "HELLO")
        assert len(result) == 1  # Case-insensitive

    def test_contains_match(self):
        rules = [{"keyword": "价格", "match_type": "CONTAINS", "reply_template": "价格说明"}]
        result = KeywordMatcher.match(rules, "请问价格是多少")
        assert len(result) == 1

    def test_starts_with_match(self):
        rules = [{"keyword": "价格", "match_type": "STARTS_WITH", "reply_template": "关于价格..."}]
        result = KeywordMatcher.match(rules, "价格是多少")
        assert len(result) == 1

    def test_starts_with_no_match(self):
        rules = [{"keyword": "价格", "match_type": "STARTS_WITH", "reply_template": "关于价格..."}]
        result = KeywordMatcher.match(rules, "请问价格是多少")
        assert len(result) == 0

    def test_ends_with_match(self):
        rules = [{"keyword": "谢谢", "match_type": "ENDS_WITH", "reply_template": "不客气！"}]
        result = KeywordMatcher.match(rules, "非常感谢谢谢")
        assert len(result) == 1

    def test_ends_with_no_match(self):
        rules = [{"keyword": "谢谢", "match_type": "ENDS_WITH", "reply_template": "不客气！"}]
        result = KeywordMatcher.match(rules, "谢谢你帮助我")
        assert len(result) == 0

    def test_regex_match(self):
        rules = [{"keyword": r"\d+号", "match_type": "REGEX", "reply_template": "房间号已记录"}]
        result = KeywordMatcher.match(rules, "我要预订108号房间")
        assert len(result) == 1

    def test_regex_no_match(self):
        rules = [{"keyword": r"^\d+$", "match_type": "REGEX", "reply_template": "纯数字"}]
        result = KeywordMatcher.match(rules, "abc123")
        assert len(result) == 0

    def test_invalid_regex_skipped(self):
        rules = [{"keyword": "[invalid", "match_type": "REGEX", "reply_template": "x"}]
        result = KeywordMatcher.match(rules, "test")
        assert len(result) == 0  # Should not crash

    def test_empty_rules(self):
        result = KeywordMatcher.match([], "hello")
        assert result == []

    def test_empty_message(self):
        rules = [{"keyword": "hi", "match_type": "CONTAINS", "reply_template": "hey"}]
        result = KeywordMatcher.match(rules, "")
        assert len(result) == 0

    def test_multiple_matches(self):
        rules = [
            {"keyword": "hello", "match_type": "CONTAINS", "reply_template": "Hi!"},
            {"keyword": "world", "match_type": "CONTAINS", "reply_template": "Earth!"},
        ]
        result = KeywordMatcher.match(rules, "hello world")
        assert len(result) == 2

    def test_apply_template_with_variables(self):
        template = "您好{name}，订单{order_id}已确认"
        result = KeywordMatcher.apply_template(template, {"name": "张三", "order_id": "12345"})
        assert result == "您好张三，订单12345已确认"

    def test_apply_template_no_variables(self):
        result = KeywordMatcher.apply_template("纯文本回复", {})
        assert result == "纯文本回复"

    def test_apply_template_missing_variable(self):
        template = "您好{name}，订单{order_id}"
        result = KeywordMatcher.apply_template(template, {"name": "李四"})
        assert result == "您好李四，订单{order_id}"

    def test_apply_template_none_value_converted(self):
        template = "值是{val}"
        result = KeywordMatcher.apply_template(template, {"val": None})
        assert result == "值是None"
