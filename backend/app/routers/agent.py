"""Agent 接口：回复建议生成 + 自动化规则 CRUD。"""
import asyncio

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.models import Contact, Rule, User
from app.schemas import (
    OkResponse,
    RuleCreateRequest,
    RuleListResponse,
    RuleOut,
    RuleUpdateRequest,
    SuggestRequest,
    SuggestResponse,
)
from app.services import agent as agent_service
from app.services import ratelimit

router = APIRouter(prefix="/agent", tags=["agent"])


def _own_contact(db: Session, user_id: int, contact_id: int) -> Contact:
    c = db.execute(
        select(Contact).where(Contact.id == contact_id, Contact.user_id == user_id)
    ).scalars().first()
    if c is None:
        raise HTTPException(status_code=404, detail="联系人不存在")
    return c


@router.post("/suggest", response_model=SuggestResponse)
def suggest(body: SuggestRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """B-5：同步 def 端点（FastAPI 线程池），DB 不阻塞事件循环。"""
    if not ratelimit.check_suggest_quota(user.id):
        raise HTTPException(status_code=429, detail="今日建议配额已用尽")
    contact = _own_contact(db, user.id, body.contact_id)
    result = asyncio.run(agent_service.process_message(db, user.id, contact, body.message, body.mode))
    return SuggestResponse(**result)


@router.get("/rules", response_model=RuleListResponse)
def list_rules(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(Rule).where(Rule.user_id == user.id).order_by(Rule.id.desc())
    ).scalars().all()
    return RuleListResponse(items=[RuleOut.model_validate(r) for r in rows])


@router.post("/rules", response_model=RuleOut)
def create_rule(body: RuleCreateRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.contact_id is not None:
        _own_contact(db, user.id, body.contact_id)
    r = Rule(user_id=user.id, contact_id=body.contact_id, mode=body.mode, keywords=body.keywords, active=body.active)
    db.add(r)
    db.commit()
    db.refresh(r)
    return RuleOut.model_validate(r)


@router.put("/rules/{rule_id}", response_model=RuleOut)
def update_rule(
    rule_id: int, body: RuleUpdateRequest,
    user: User = Depends(get_current_user), db: Session = Depends(get_db),
):
    r = db.execute(
        select(Rule).where(Rule.id == rule_id, Rule.user_id == user.id)
    ).scalars().first()
    if r is None:
        raise HTTPException(status_code=404, detail="规则不存在")
    if body.contact_id is not None:
        _own_contact(db, user.id, body.contact_id)
    for k, v in body.model_dump(exclude_unset=True, exclude_none=True).items():
        setattr(r, k, v)
    db.commit()
    db.refresh(r)
    return RuleOut.model_validate(r)


@router.delete("/rules/{rule_id}", response_model=OkResponse)
def delete_rule(rule_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    r = db.execute(
        select(Rule).where(Rule.id == rule_id, Rule.user_id == user.id)
    ).scalars().first()
    if r is None:
        raise HTTPException(status_code=404, detail="规则不存在")
    db.delete(r)
    db.commit()
    return OkResponse(ok=True)
