"""卡密：激活 / 信息 / 注销 + admin 批量生成 / 列表 / 吊销（Kill Switch）。

- 激活用单条原子 UPDATE（H-2），避免 check-then-act 竞态导致一卡多用
- 已注销（inactive）的卡密可重新激活（B-3），便于设备迁移
- 激活按 IP 限频（H-3）
"""
import secrets
import string
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user, require_admin
from app.models import License, User
from app.schemas import (
    ActivateRequest,
    ActivateResponse,
    AdminLicenseCreateRequest,
    AdminLicenseCreateResponse,
    AdminLicenseItem,
    AdminLicenseListResponse,
    DeactivateRequest,
    LicenseInfoResponse,
    LicenseOut,
    OkResponse,
)
from app.services import ratelimit

router = APIRouter(prefix="/license", tags=["license"])

_ALPHABET = string.ascii_uppercase + string.digits


def _now_naive() -> datetime:
    """SQLite 读出的是 naive datetime，统一用 naive UTC 比较。"""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _gen_code() -> str:
    return "-".join("".join(secrets.choice(_ALPHABET) for _ in range(4)) for _ in range(3))


def _license_out(lic: License) -> LicenseOut:
    return LicenseOut(
        code=lic.code,
        expires_at=lic.expires_at,
        features=lic.features or {},
        status=lic.status,
    )


@router.post("/activate", response_model=ActivateResponse)
def activate(body: ActivateRequest, request: Request, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    ip = request.client.host if request.client else "unknown"
    if not ratelimit.check_activate_rate(ip):
        raise HTTPException(status_code=429, detail="激活过于频繁，请稍后再试")

    code = body.code.strip().upper()
    now = _now_naive()
    # 原子占用：unused / inactive 均可激活；未过期
    stmt = (
        update(License)
        .where(
            License.code == code,
            License.status.in_(["unused", "inactive"]),
            License.expires_at > now,
        )
        .values(
            status="active",
            user_id=user.id,
            device_fingerprint=body.device_fingerprint,
            activated_at=now,
        )
    )
    result = db.execute(stmt)
    db.commit()
    if result.rowcount == 0:
        lic = db.execute(select(License).where(License.code == code)).scalars().first()
        if lic is None:
            raise HTTPException(status_code=404, detail="卡密不存在")
        if lic.expires_at <= now:
            raise HTTPException(status_code=400, detail="卡密已过期")
        raise HTTPException(status_code=400, detail=f"卡密状态为 {lic.status}，不可激活")
    lic = db.execute(select(License).where(License.code == code)).scalars().first()
    return ActivateResponse(activated=True, license=_license_out(lic))


@router.get("/info", response_model=LicenseInfoResponse)
def info(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    now = _now_naive()
    lic = db.execute(
        select(License).where(License.user_id == user.id, License.status == "active")
    ).scalars().first()
    if lic is None or lic.expires_at <= now:
        if lic is not None and lic.expires_at <= now:
            lic.status = "expired"
            db.commit()
        return LicenseInfoResponse(valid=False)
    return LicenseInfoResponse(valid=True, license=_license_out(lic))


@router.post("/deactivate", response_model=OkResponse)
def deactivate(body: DeactivateRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """注销：卡密回到 inactive，可在其他设备重新激活（B-3）。"""
    lic = db.execute(
        select(License).where(
            License.user_id == user.id,
            License.device_fingerprint == body.device_fingerprint,
            License.status == "active",
        )
    ).scalars().first()
    if lic is not None:
        lic.status = "inactive"
        lic.device_fingerprint = None
        lic.user_id = None
        lic.activated_at = None
        db.commit()
    return OkResponse(ok=True)


@router.post("/admin", response_model=AdminLicenseCreateResponse)
def admin_create(body: AdminLicenseCreateRequest, _: User = Depends(require_admin), db: Session = Depends(get_db)):
    now = _now_naive()
    expires = now + timedelta(days=body.days)
    codes = []
    for _ in range(body.count):
        code = _gen_code()
        db.add(License(code=code, status="unused", expires_at=expires, features=body.features))
        codes.append(code)
    db.commit()
    return AdminLicenseCreateResponse(codes=codes)


@router.get("/admin", response_model=AdminLicenseListResponse)
def admin_list(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    total = db.execute(select(func.count(License.id))).scalar_one()
    rows = (
        db.execute(select(License).order_by(License.id.desc()).offset(offset).limit(limit))
        .scalars()
        .all()
    )
    # 一次 join 取用户名，避免 N+1
    user_ids = {r.user_id for r in rows if r.user_id}
    users = {
        u.id: u.username
        for u in db.execute(select(User).where(User.id.in_(user_ids))).scalars().all()
    } if user_ids else {}
    items = [
        AdminLicenseItem(
            code=r.code,
            status=r.status,
            activated_by=users.get(r.user_id) if r.user_id else None,
            device_fingerprint=r.device_fingerprint,
            expires_at=r.expires_at,
            features=r.features or {},
            created_at=r.created_at,
        )
        for r in rows
    ]
    return AdminLicenseListResponse(items=items, total=total)


@router.post("/admin/{code}/revoke", response_model=OkResponse)
def admin_revoke(code: str, _: User = Depends(require_admin), db: Session = Depends(get_db)):
    """吊销 = Kill Switch：该卡密对应实例所有自动化立即失效。"""
    lic = db.execute(select(License).where(License.code == code.strip().upper())).scalars().first()
    if lic is None:
        raise HTTPException(status_code=404, detail="卡密不存在")
    lic.status = "revoked"
    db.commit()
    return OkResponse(ok=True)
