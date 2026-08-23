"""人设 CRUD。

B-1：is_global 仅管理员可设置（普通用户强制 False，避免污染所有用户 AI 上下文）；
is_default 为「用户自己的默认人设」，普通用户可设置。
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user, require_admin
from app.models import Persona, User
from app.schemas import (
    OkResponse,
    PersonaCreateRequest,
    PersonaListResponse,
    PersonaOut,
    PersonaUpdateRequest,
)

router = APIRouter(prefix="/personas", tags=["personas"])


def _own(db: Session, user_id: int, persona_id: int) -> Persona:
    p = db.execute(
        select(Persona).where(Persona.id == persona_id, Persona.user_id == user_id)
    ).scalars().first()
    if p is None:
        raise HTTPException(status_code=404, detail="人设不存在")
    return p


def _is_admin(user: User) -> bool:
    return user.role == "admin"


@router.get("", response_model=PersonaListResponse)
def list_personas(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(Persona).where(Persona.user_id == user.id).order_by(Persona.id.desc())
    ).scalars().all()
    return PersonaListResponse(items=[PersonaOut.model_validate(r) for r in rows])


@router.post("", response_model=PersonaOut)
def create_persona(body: PersonaCreateRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    is_global = body.is_global if _is_admin(user) else False  # 非 admin 强制 False
    p = Persona(
        user_id=user.id,
        name=body.name,
        config=body.config,
        is_default=body.is_default,
        is_global=is_global,
    )
    db.add(p)
    db.commit()
    db.refresh(p)
    return PersonaOut.model_validate(p)


@router.put("/{persona_id}", response_model=PersonaOut)
def update_persona(
    persona_id: int, body: PersonaUpdateRequest,
    user: User = Depends(get_current_user), db: Session = Depends(get_db),
):
    p = _own(db, user.id, persona_id)
    data = body.model_dump(exclude_unset=True)
    if not _is_admin(user):
        data.pop("is_global", None)  # 非 admin 不可修改全局标记
    for k, v in data.items():
        if v is not None:
            setattr(p, k, v)
    db.commit()
    db.refresh(p)
    return PersonaOut.model_validate(p)


@router.delete("/{persona_id}", response_model=OkResponse)
def delete_persona(persona_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    p = _own(db, user.id, persona_id)
    db.delete(p)
    db.commit()
    return OkResponse(ok=True)
