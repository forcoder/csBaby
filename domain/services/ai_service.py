import json
import time
import urllib.request
import urllib.error
from typing import Dict, Any, List


class AIService:
    def call_model(
        self,
        model_config: Dict[str, Any],
        messages: List[Dict[str, str]],
        temperature: float,
        max_tokens: int,
    ) -> Dict[str, Any]:
        start = time.time()
        model_type = model_config.get("model_type", "OPENAI").upper()
        api_key = model_config.get("api_key", "")
        if not api_key:
            raise ValueError("API key is required for AI model calls")
        model = model_config.get("model", "gpt-4o")
        endpoint = model_config.get("api_endpoint", "")
        timeout = model_config.get("timeout_seconds", 60)
        if not isinstance(timeout, (int, float)) or timeout < 1 or timeout > 300:
            timeout = 60

        if model_type == "CLAUDE":
            url = endpoint or "https://api.anthropic.com/v1/messages"
            data = {"model": model or "claude-3-5-sonnet-20241022", "max_tokens": max_tokens, "temperature": temperature, "messages": messages}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"x-api-key": api_key, "Content-Type": "application/json",
                         "anthropic-version": "2023-06-01",
                         "anthropic-dangerous-direct-browser-access": "true"},
            )
        elif model_type == "ZHIPU":
            url = endpoint or "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            data = {"model": model or "glm-4", "messages": messages, "temperature": temperature, "max_tokens": max_tokens}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            )
        elif model_type == "TONGYI":
            url = endpoint or "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            data = {"model": model or "qwen-turbo", "input": {"messages": messages},
                    "parameters": {"temperature": temperature, "max_tokens": max_tokens}}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            )
        else:
            url = endpoint or "https://api.openai.com/v1/chat/completions"
            data = {"model": model, "messages": messages, "temperature": temperature, "max_tokens": max_tokens}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            )

        try:
            resp = urllib.request.urlopen(req, timeout=timeout)
            result = json.loads(resp.read())
        except urllib.error.HTTPError as e:
            error_body = e.read().decode("utf-8", errors="replace")
            raise Exception(f"AI API HTTP {e.code}: {error_body}") from e
        except (urllib.error.URLError, OSError) as e:
            raise Exception(f"AI API request failed: {e}") from e

        if model_type == "CLAUDE":
            content = result.get("content") or []
            reply = content[0].get("text", "") if content and isinstance(content[0], dict) else ""
            usage = result.get("usage") or {}
            tokens = (usage.get("input_tokens") or 0) + (usage.get("output_tokens") or 0)
        else:
            choices = result.get("choices") or []
            reply = ""
            if choices and isinstance(choices[0], dict):
                msg = choices[0].get("message") or {}
                reply = msg.get("content", "")
            tokens = (result.get("usage") or {}).get("total_tokens", 0)

        elapsed = int((time.time() - start) * 1000)
        return {"reply": reply, "tokens_used": tokens, "response_time_ms": elapsed, "model_used": model}
