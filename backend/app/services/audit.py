"""审计日志。"""
from sqlalchemy.orm import Session

from app.models import AuditLog


def log_event(db: Session, user_id: int | None, event: str, payload: dict | None = None) -> AuditLog:
    """只入队不提交，由调用方统一 commit（事务收敛）。"""
    entry = AuditLog(user_id=user_id, event=event, payload=payload or {})
    db.add(entry)
    return entry
