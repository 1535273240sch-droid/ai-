"""认证：注册 / 登录 / 当前用户。登录失败按用户名+IP 锁定（H-3 限流）。"""
from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import security
from app.config import settings
from app.db import get_db
from app.deps import get_current_user
from app.models import User
from app.schemas import AuthResponse, LoginRequest, MeResponse, RegisterRequest, UserOut
from app.services import ratelimit

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=AuthResponse)
def register(body: RegisterRequest, db: Session = Depends(get_db)):
    if db.execute(select(User).where(User.username == body.username)).scalars().first():
        raise HTTPException(status_code=400, detail="用户名已存在")
    # 管理员只能由 seed 或已有管理员创建（B-6：禁止首个注册用户自动提权）
    user = User(username=body.username, password_hash=security.hash_password(body.password), role="user")
    db.add(user)
    db.commit()
    db.refresh(user)
    return AuthResponse(
        token=security.create_access_token(user.id, user.username, user.role),
        user=UserOut.model_validate(user),
    )


@router.post("/login", response_model=AuthResponse)
def login(body: LoginRequest, request: Request, db: Session = Depends(get_db)):
    ip = request.client.host if request.client else "unknown"
    key = f"{body.username}:{ip}"
    if ratelimit.is_login_locked(key):
        raise HTTPException(
            status_code=429,
            detail=f"失败次数过多，账号已锁定 {settings.LOGIN_LOCK_MINUTES} 分钟",
        )
    user = db.execute(select(User).where(User.username == body.username)).scalars().first()
    if user is None or not security.verify_password(body.password, user.password_hash):
        failures = ratelimit.record_login_failure(key)
        if failures >= settings.LOGIN_MAX_FAILURES:
            raise HTTPException(status_code=429, detail="失败次数过多，账号已锁定")
        raise HTTPException(status_code=401, detail="用户名或密码错误")
    ratelimit.clear_login_failures(key)
    return AuthResponse(
        token=security.create_access_token(user.id, user.username, user.role),
        user=UserOut.model_validate(user),
    )


@router.get("/me", response_model=MeResponse)
def me(user: User = Depends(get_current_user)):
    return MeResponse(user=UserOut.model_validate(user))
