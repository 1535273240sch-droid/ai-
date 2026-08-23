"""联系人画像 CRUD。"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user
from app.models import Contact, User
from app.schemas import (
    ContactCreateRequest,
    ContactListResponse,
    ContactOut,
    ContactUpdateRequest,
    OkResponse,
)

router = APIRouter(prefix="/contacts", tags=["contacts"])


def _own(db: Session, user_id: int, contact_id: int) -> Contact:
    c = db.execute(
        select(Contact).where(Contact.id == contact_id, Contact.user_id == user_id)
    ).scalars().first()
    if c is None:
        raise HTTPException(status_code=404, detail="联系人不存在")
    return c


@router.get("", response_model=ContactListResponse)
def list_contacts(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(Contact).where(Contact.user_id == user.id).order_by(Contact.id.desc())
    ).scalars().all()
    return ContactListResponse(items=[ContactOut.model_validate(r) for r in rows])


@router.post("", response_model=ContactOut)
def create_contact(body: ContactCreateRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    dup = db.execute(
        select(Contact).where(
            Contact.user_id == user.id,
            Contact.platform == body.platform,
            Contact.platform_contact_id == body.platform_contact_id,
        )
    ).scalars().first()
    if dup:
        raise HTTPException(status_code=400, detail="该联系人已存在")
    c = Contact(
        user_id=user.id,
        platform=body.platform,
        platform_contact_id=body.platform_contact_id,
        nickname=body.nickname,
        profile=body.profile.model_dump(exclude_none=True),
    )
    db.add(c)
    db.commit()
    db.refresh(c)
    return ContactOut.model_validate(c)


@router.put("/{contact_id}", response_model=ContactOut)
def update_contact(
    contact_id: int, body: ContactUpdateRequest,
    user: User = Depends(get_current_user), db: Session = Depends(get_db),
):
    c = _own(db, user.id, contact_id)
    data = body.model_dump(exclude_unset=True)
    if "profile" in data and isinstance(data["profile"], dict):
        merged = dict(c.profile or {})
        merged.update({k: v for k, v in data["profile"].items() if v is not None})
        data["profile"] = merged
    for k, v in data.items():
        setattr(c, k, v)
    db.commit()
    db.refresh(c)
    return ContactOut.model_validate(c)


@router.delete("/{contact_id}", response_model=OkResponse)
def delete_contact(contact_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    c = _own(db, user.id, contact_id)
    db.delete(c)
    db.commit()
    return OkResponse(ok=True)
