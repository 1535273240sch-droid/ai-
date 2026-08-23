"""Model Gateway：OpenAI 兼容多模型接入；无 key / 调用失败时返回 mock 建议，保证无 key 可测。"""
import json
import logging
from typing import Any

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

_MOCK_SUGGESTIONS = [
    "哈哈，这个话题有意思，你继续说～",
    "嗯嗯，我明白你的意思了，然后呢？",
    "这样啊，那你觉得怎么样？",
]


def _mock_suggestions(context_tail: str) -> list[str]:
    """无 key 时的兜底建议，前缀带上上下文尾，便于识别为 mock。"""
    tail = context_tail[-20:] if context_tail else ""
    return [
        f"[mock] 针对「{tail}」的回复：哈哈有意思，你继续说～",
        f"[mock] 针对「{tail}」的回复：嗯嗯明白了，然后呢？",
        f"[mock] 针对「{tail}」的回复：这样啊，你觉得呢？",
    ]


def parse_suggestions(content: str, limit: int = 3) -> list[str]:
    """容错解析 LLM 输出：JSON（suggestions/replies/results/reply/suggestion 多种键）或纯文本行。"""
    text = content.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:].strip()
    suggestions: list[str] = []
    # 尝试 JSON
    try:
        data = json.loads(text)
        if isinstance(data, dict):
            for key in ("suggestions", "replies", "results", "reply", "suggestion"):
                val = data.get(key)
                if isinstance(val, list):
                    suggestions = [str(x) for x in val if str(x).strip()]
                    break
                if isinstance(val, str) and val.strip():
                    suggestions = [val.strip()]
                    break
            if not suggestions and isinstance(data.get("choices"), list):
                for c in data["choices"]:
                    msg = (c.get("message") or {}).get("content") or c.get("text") or ""
                    if msg.strip():
                        suggestions.append(msg.strip())
    except (json.JSONDecodeError, AttributeError):
        pass
    # 兜底：按行分割
    if not suggestions:
        lines = [ln.strip().strip("123.、-*") for ln in text.splitlines() if ln.strip()]
        suggestions = lines
    seen, out = set(), []
    for s in suggestions:
        if s and s not in seen:
            seen.add(s)
            out.append(s)
        if len(out) >= limit:
            break
    return out or ["好的，收到！"]


async def generate_suggestions(context_messages: list[dict[str, str]], limit: int = 3) -> list[str]:
    """调用 OpenAI 兼容 chat/completions 生成回复建议。

    context_messages: [{"role": "system"|"user"|"assistant", "content": str}]
    """
    if not settings.OPENAI_API_KEY:
        return _mock_suggestions(context_messages[-1]["content"] if context_messages else "")
    url = settings.OPENAI_BASE_URL.rstrip("/") + "/chat/completions"
    if not url.startswith("https://"):
        logger.warning("OPENAI_BASE_URL 非 https，拒绝请求（SSRF 防护）")
        return _mock_suggestions(context_messages[-1]["content"] if context_messages else "")
    body: dict[str, Any] = {
        "model": settings.OPENAI_MODEL,
        "messages": context_messages,
        "temperature": 0.8,
        "max_tokens": 500,
    }
    headers = {"Authorization": f"Bearer {settings.OPENAI_API_KEY}"}
    try:
        async with httpx.AsyncClient(
            timeout=settings.OPENAI_TIMEOUT_SECONDS, allow_redirects=False
        ) as client:
            resp = await client.post(url, json=body, headers=headers)
            resp.raise_for_status()
            data = resp.json()
        content = data["choices"][0]["message"]["content"]
        parsed = parse_suggestions(content, limit)
        return parsed or _mock_suggestions(context_messages[-1]["content"] if context_messages else "")
    except Exception as exc:  # 网络/限流/解析失败都兜底
        logger.warning("LLM 调用失败，使用 mock：%s", exc)
        return _mock_suggestions(context_messages[-1]["content"] if context_messages else "")
