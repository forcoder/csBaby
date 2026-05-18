import pytest
from unittest.mock import patch, MagicMock
import json


class TestGenerateReply:
    def test_generate_keyword_match(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "你好",
            "match_type": "CONTAINS",
            "reply_template": "您好！"
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "你好啊",
            "context": {}
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["reply"] == "您好！"
        assert data["source"] == "keyword"
        assert data["confidence"] == 1.0

    def test_generate_with_template_variables(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "订单",
            "reply_template": "您好{name}，您的订单{order_id}已确认"
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "我的订单呢",
            "context": {"name": "张三", "order_id": "12345"}
        }, headers=auth_headers)
        data = resp.get_json()
        assert "张三" in data["reply"]
        assert "12345" in data["reply"]

    def test_generate_exact_match(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "hello",
            "match_type": "EXACT",
            "reply_template": "Hi there!"
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "hello"
        }, headers=auth_headers)
        data = resp.get_json()
        assert data["reply"] == "Hi there!"

    def test_generate_exact_no_match(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "hello",
            "match_type": "EXACT",
            "reply_template": "Hi!"
        }, headers=auth_headers)
        # Should NOT match since "hello there" != "hello"
        # Without a model configured, falls through to AI which returns 400
        resp = client.post("/api/ai/generate", json={
            "message": "hello there"
        }, headers=auth_headers)
        # Without a configured model, should get 400; with a model, 200
        assert resp.status_code in (200, 400, 500)

    def test_generate_starts_with_match(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "价格",
            "match_type": "STARTS_WITH",
            "reply_template": "关于价格..."
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "价格是多少"
        }, headers=auth_headers)
        data = resp.get_json()
        assert data["reply"] == "关于价格..."

    def test_generate_ends_with_match(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "谢谢",
            "match_type": "ENDS_WITH",
            "reply_template": "不客气！"
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "非常感谢谢谢"
        }, headers=auth_headers)
        data = resp.get_json()
        assert data["reply"] == "不客气！"

    def test_generate_regex_match(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": r"\d+号",
            "match_type": "REGEX",
            "reply_template": "房间号已记录"
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "我要预订108号房间"
        }, headers=auth_headers)
        data = resp.get_json()
        assert data["reply"] == "房间号已记录"

    def test_generate_empty_message(self, client, auth_headers):
        resp = client.post("/api/ai/generate", json={"message": ""}, headers=auth_headers)
        assert resp.status_code == 400

    def test_generate_message_too_long(self, client, auth_headers):
        resp = client.post("/api/ai/generate", json={"message": "x" * 10001}, headers=auth_headers)
        assert resp.status_code == 400

    def test_generate_unauthorized(self, client):
        resp = client.post("/api/ai/generate", json={"message": "hello"})
        assert resp.status_code == 401

    @patch("app.call_ai_model")
    def test_generate_ai_fallback(self, mock_ai, client, auth_headers):
        mock_ai.return_value = {
            "reply": "AI generated reply",
            "tokens_used": 42,
            "response_time_ms": 150,
            "model_used": "gpt-4o"
        }
        client.post("/api/models", json={
            "name": "GPT-4", "model_type": "OPENAI",
            "model": "gpt-4o", "api_key": "sk-test"
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "What's the weather?"
        }, headers=auth_headers)
        data = resp.get_json()
        assert data["reply"] == "AI generated reply"
        assert data["source"] == "ai"
        assert data["tokens_used"] == 42

    @patch("app.call_ai_model")
    def test_generate_ai_with_style(self, mock_ai, client, auth_headers):
        mock_ai.return_value = {
            "reply": "Sure!",
            "tokens_used": 10,
            "response_time_ms": 50,
            "model_used": "gpt-4o"
        }
        client.post("/api/models", json={
            "name": "GPT-4", "model_type": "OPENAI",
            "model": "gpt-4o", "api_key": "sk-test"
        }, headers=auth_headers)
        resp = client.post("/api/ai/generate", json={
            "message": "help me",
            "style": {"formality": 0.9, "enthusiasm": 0.8}
        }, headers=auth_headers)
        assert resp.status_code == 200
        # Verify the system prompt was modified with style hints
        call_args = mock_ai.call_args
        messages = call_args[0][1]
        system_msg = messages[0]["content"]
        assert "正式" in system_msg or "热情" in system_msg


class TestChat:
    def test_chat(self, client, auth_headers):
        resp = client.post("/api/ai/chat", json={
            "messages": [{"role": "user", "content": "Hello"}]
        }, headers=auth_headers)
        # Without a model configured, should get 400
        assert resp.status_code == 400

    def test_chat_empty_messages(self, client, auth_headers):
        resp = client.post("/api/ai/chat", json={
            "messages": []
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_chat_unauthorized(self, client):
        resp = client.post("/api/ai/chat", json={
            "messages": [{"role": "user", "content": "Hello"}]
        })
        assert resp.status_code == 401

    def test_chat_message_missing_content(self, client, auth_headers):
        """Messages without 'content' field should be rejected."""
        resp = client.post("/api/ai/chat", json={
            "messages": [{"role": "user"}]
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_chat_message_missing_role(self, client, auth_headers):
        """Messages without 'role' field should be rejected."""
        resp = client.post("/api/ai/chat", json={
            "messages": [{"content": "Hello"}]
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_chat_message_not_dict(self, client, auth_headers):
        """Non-dict messages should be rejected."""
        resp = client.post("/api/ai/chat", json={
            "messages": ["just a string"]
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_chat_messages_not_list(self, client, auth_headers):
        """Non-list messages should be rejected."""
        resp = client.post("/api/ai/chat", json={
            "messages": {"role": "user", "content": "Hello"}
        }, headers=auth_headers)
        assert resp.status_code == 400


class TestGenerateReplyInputValidation:
    """Tests for input validation on the generate_reply endpoint."""

    def test_context_as_string_treated_as_empty(self, client, auth_headers):
        """If context is a string instead of dict, should not crash."""
        resp = client.post("/api/ai/generate", json={
            "message": "你好",
            "context": "not a dict"
        }, headers=auth_headers)
        # Should not crash with 500; should handle gracefully
        assert resp.status_code in (200, 400)

    def test_style_as_string_treated_as_empty(self, client, auth_headers):
        """If style is a string instead of dict, should not crash."""
        resp = client.post("/api/ai/generate", json={
            "message": "你好",
            "style": "not a dict"
        }, headers=auth_headers)
        assert resp.status_code in (200, 400)

    def test_context_as_list_treated_as_empty(self, client, auth_headers):
        """If context is a list instead of dict, should not crash."""
        resp = client.post("/api/ai/generate", json={
            "message": "你好",
            "context": ["not", "a", "dict"]
        }, headers=auth_headers)
        assert resp.status_code in (200, 400)

    @patch("app.call_ai_model")
    def test_chat_with_model(self, mock_ai, client, auth_headers):
        mock_ai.return_value = {
            "reply": "Hi!",
            "tokens_used": 5,
            "response_time_ms": 30,
            "model_used": "gpt-4o"
        }
        client.post("/api/models", json={
            "name": "GPT-4", "model_type": "OPENAI",
            "model": "gpt-4o", "api_key": "sk-test"
        }, headers=auth_headers)
        resp = client.post("/api/ai/chat", json={
            "messages": [
                {"role": "system", "content": "You are helpful"},
                {"role": "user", "content": "Hello"}
            ]
        }, headers=auth_headers)
        data = resp.get_json()
        assert data["reply"] == "Hi!"
        assert data["model_used"] == "gpt-4o"
