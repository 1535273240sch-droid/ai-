"""平台上报入口：Android 端上报收到/发出消息，写 Message 表（供统计与记忆）。"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.models import Contact, Message, User
from app.schemas import OkResponse, PlatformEventRequest, PlatformReplyRequest
from app.services import memory
from app.services.audit import log_event

router = APIRouter(prefix="/platform", tags=["platform"])


def _resolve_contact(db: Session, user_id: int, platform: str, platform_contact_id: str) -> Contact:
    c = db.execute(
        select(Contact).where(
            Contact.user_id == user_id,
            Contact.platform == platform,
            Contact.platform_contact_id == platform_contact_id,
        )
    ).scalars().first()
    if c is None:
        raise HTTPException(status_code=404, detail="联系人未配置，请先在后台添加")
    return c


@router.post("/events", response_model=OkResponse)
def platform_event(body: PlatformEventRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    contact = _resolve_contact(db, user.id, body.platform, body.platform_contact_id)
    if body.type == "message_received" and body.content:
        msg = Message(user_id=user.id, contact_id=contact.id, direction="in", content=body.content)
        db.add(msg)
        memory.add_message(db, contact.id, "user", body.content)
        log_event(db, user.id, "platform.message_received", {"contact_id": contact.id})
    elif body.type == "message_sent" and body.content:
        msg = Message(user_id=user.id, contact_id=contact.id, direction="out", content=body.content)
        db.add(msg)
        memory.add_message(db, contact.id, "assistant", body.content)
        log_event(db, user.id, "platform.message_sent", {"contact_id": contact.id})
    else:
        log_event(db, user.id, f"platform.{body.type}", {"contact_id": contact.id, "platform": body.platform})
    db.commit()
    return OkResponse(ok=True)


@router.post("/reply", response_model=OkResponse)
def platform_reply(body: PlatformReplyRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Android 端发送成功后回执，计入发出消息。"""
    contact = _resolve_contact(db, user.id, body.platform, body.platform_contact_id)
    db.add(Message(user_id=user.id, contact_id=contact.id, direction="out", content=body.content))
    memory.add_message(db, contact.id, "assistant", body.content)
    log_event(db, user.id, "platform.reply", {"contact_id": contact.id})
    db.commit()
    return OkResponse(ok=True)
