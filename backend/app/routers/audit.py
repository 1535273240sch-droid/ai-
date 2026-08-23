"""审计日志查询。"""
from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.models import AuditLog, User
from app.schemas import AuditLogListResponse, AuditLogOut

router = APIRouter(prefix="/audit", tags=["audit"])


@router.get("/logs", response_model=AuditLogListResponse)
def logs(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    q = select(AuditLog)
    if user.role != "admin":
        q = q.where(AuditLog.user_id == user.id)
    total = db.execute(select(func.count()).select_from(q.subquery())).scalar_one()
    rows = db.execute(q.order_by(AuditLog.id.desc()).offset(offset).limit(limit)).scalars().all()
    return AuditLogListResponse(items=[AuditLogOut.model_validate(r) for r in rows], total=total)
