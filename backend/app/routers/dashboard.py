"""管理看板统计。"""
from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import require_admin
from app.models import AuditLog, Contact, License, Message, Persona, User
from app.schemas import AuditLogOut, StatsResponse

router = APIRouter(prefix="/dashboard", tags=["dashboard"])


@router.get("/stats", response_model=StatsResponse)
def stats(_: User = Depends(require_admin), db: Session = Depends(get_db)):
    message_count = db.execute(select(func.count(Message.id))).scalar_one()
    reply_count = db.execute(
        select(func.count(Message.id)).where(Message.direction == "out")
    ).scalar_one()
    active_contacts = db.execute(select(func.count(Contact.id))).scalar_one()
    persona_count = db.execute(select(func.count(Persona.id))).scalar_one()
    license_count = db.execute(select(func.count(License.id))).scalar_one()
    active_license_count = db.execute(
        select(func.count(License.id)).where(License.status == "active")
    ).scalar_one()
    recent = db.execute(
        select(AuditLog).order_by(AuditLog.id.desc()).limit(10)
    ).scalars().all()
    return StatsResponse(
        message_count=message_count,
        reply_count=reply_count,
        active_contacts=active_contacts,
        persona_count=persona_count,
        license_count=license_count,
        active_license_count=active_license_count,
        recent_logs=[AuditLogOut.model_validate(r) for r in recent],
    )
