"""Agent Core：Event → Context → Memory → Decision（规则引擎+LLM）→ Action → Result。

每次消息处理流程：
1. 校验卡密（吊销/过期即 Kill Switch）
2. 规则引擎：内置敏感词 + 用户 rules.keywords，对所有模式生效（H-1，命中一律转人工）
3. 服务端 mode 裁决：匹配的 active 规则优先，其次客户端请求参数
4. 组装上下文：全局/默认人设 + 联系人画像 + 长期摘要 + 最近对话
5. LLM 生成 3 条建议（Model Gateway）
6. 节奏调度计算 delay_ms
7. 写入记忆 + 审计（commit 由调用方统一收敛），并通过 WebSocket 推送建议事件（B-4）
"""
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.models import Contact, License, Persona, Rule
from app.services import gateway, memory, scheduler

SYSTEM_PROMPT_TEMPLATE = (
    "你是一个{role}的社交聊天回复建议助手。根据以下【人设】、【对方画像】、【长期记忆】和【最近对话】"
    "生成恰好 3 条不同的回复建议，要求：\n"
    "1. 风格不同：一条正经、一条幽默、一条简短\n"
    "2. 自然像真人，不要机械\n"
    "3. 适当用 emoji 但不过度\n"
    "4. 严格遵守【禁忌话题】\n"
    "5. 只输出 JSON 格式：{{\"suggestions\": [\"建议1\", \"建议2\", \"建议3\"]}}\n"
    "【人设】{persona}\n"
    "【对方画像】{profile}\n"
    "【长期记忆】{summary}\n"
    "【禁忌话题】{taboos}"
)


def _license_valid(db: Session, user_id: int) -> bool:
    """用户至少有一个 active 且未过期的卡密，否则视为 Kill Switch 生效。"""
    now = datetime.now(timezone.utc).replace(tzinfo=None)  # SQLite 读出为 naive
    row = db.execute(
        select(License).where(
            License.user_id == user_id,
            License.status == "active",
            License.expires_at > now,
        )
    ).scalars().first()
    return row is not None


def _pick_persona(db: Session, user_id: int) -> Persona | None:
    """优先全局默认人设，其次用户默认人设。"""
    g = db.execute(
        select(Persona).where(Persona.is_global.is_(True), Persona.is_default.is_(True))
    ).scalars().first()
    if g:
        return g
    p = db.execute(
        select(Persona)
        .where(Persona.user_id == user_id, Persona.is_default.is_(True))
    ).scalars().first()
    return p


def _pick_mode(db: Session, user_id: int, contact_id: int, requested_mode: str) -> tuple[str, str]:
    """服务端可信 mode 来源（H-1）：匹配的 active 规则优先，其次请求参数。"""
    rows = db.execute(
        select(Rule).where(
            Rule.user_id == user_id,
            Rule.active.is_(True),
            (Rule.contact_id.is_(None)) | (Rule.contact_id == contact_id),
        )
    ).scalars().all()
    if rows:
        return rows[0].mode, f"命中规则 #{rows[0].id}（{rows[0].mode}）"
    return requested_mode, "使用请求模式"


def _hit_sensitive(db: Session, user_id: int, contact_id: int, message: str) -> str | None:
    """内置敏感词 + 用户 rules.keywords 合并检查。"""
    keywords = list(settings.sensitive_keyword_list)
    rows = db.execute(
        select(Rule).where(
            Rule.user_id == user_id,
            Rule.active.is_(True),
            (Rule.contact_id.is_(None)) | (Rule.contact_id == contact_id),
        )
    ).scalars().all()
    for r in rows:
        keywords.extend(r.keywords or [])
    for kw in keywords:
        if kw and kw in message:
            return kw
    return None


async def process_message(
    db: Session,
    user_id: int,
    contact: Contact,
    message: str,
    mode: str,
) -> dict:
    """核心入口，返回 {suggestions, decision, delay_ms}。"""
    from app.services.audit import log_event  # 延迟导入避免循环

    if not _license_valid(db, user_id):
        return {
            "suggestions": [],
            "decision": {"mode": "manual", "reason": "卡密无效或已吊销（Kill Switch）"},
            "delay_ms": 0,
        }

    # 规则引擎：敏感词对所有模式生效（H-1）
    hit = _hit_sensitive(db, user_id, contact.id, message)
    if hit:
        log_event(db, user_id, "agent.manual_takeover", {"contact_id": contact.id, "keyword": hit})
        db.commit()
        return {
            "suggestions": [],
            "decision": {"mode": "manual", "reason": f"命中敏感词「{hit}」转人工"},
            "delay_ms": 0,
        }

    # 服务端 mode 裁决（rules 表）
    effective_mode, mode_reason = _pick_mode(db, user_id, contact.id, mode)
    if effective_mode == "manual":
        log_event(db, user_id, "agent.manual_rule", {"contact_id": contact.id, "reason": mode_reason})
        db.commit()
        return {
            "suggestions": [],
            "decision": {"mode": "manual", "reason": mode_reason},
            "delay_ms": 0,
        }

    persona = _pick_persona(db, user_id)
    persona_text = (
        str(persona.config) if persona and persona.config else "普通朋友，随意自然，像正常聊天"
    )
    profile = contact.profile or {}
    taboos = profile.get("taboos") or []

    # 组装上下文
    summary = memory.summarize_long_term(db, contact.id)
    recent = memory.get_recent(db, contact.id)
    system_prompt = SYSTEM_PROMPT_TEMPLATE.format(
        role=profile.get("relationship", "普通朋友"),
        persona=persona_text,
        profile=f"互动风格：{profile.get('interaction_style', '随意')}；回复频率：{profile.get('reply_frequency', '正常')}；句式：{profile.get('sentence_style', '短句为主')}",
        summary=summary or "（暂无）",
        taboos="、".join(taboos) if taboos else "（无）",
    )
    messages: list[dict[str, str]] = [{"role": "system", "content": system_prompt}]
    for m in recent:
        messages.append({"role": m["role"], "content": m["content"]})
    messages.append({"role": "user", "content": message})

    suggestions = await gateway.generate_suggestions(messages, limit=3)
    delay_ms = scheduler.compute_delay_ms(message)

    # 写入记忆 + 审计（commit 统一在调用方）
    memory.add_message(db, contact.id, "user", message)
    decision_mode = "auto" if effective_mode == "auto" else "suggest"
    log_event(db, user_id, "agent.suggest", {
        "contact_id": contact.id,
        "mode": mode,
        "effective_mode": effective_mode,
        "decision": decision_mode,
        "suggestions": suggestions,
        "delay_ms": delay_ms,
    })
    db.commit()

    # WebSocket 推送建议事件（B-4：真实调用，供 Android 端实时展示）
    try:
        from app.routers.ws import manager

        await manager.broadcast(user_id, {
            "type": "suggestion",
            "contact_id": contact.id,
            "message": message,
            "suggestions": suggestions,
            "delay_ms": delay_ms,
        })
    except Exception:
        pass  # WS 推送失败不影响主流程

    return {
        "suggestions": suggestions,
        "decision": {"mode": decision_mode, "reason": f"规则通过（{mode_reason}），AI 生成建议"},
        "delay_ms": delay_ms,
    }
