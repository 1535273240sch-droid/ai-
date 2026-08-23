"""FastAPI 入口。"""
import logging
import secrets
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import select

from app.config import settings
from app.db import Base, SessionLocal, engine
from app.models import User
from app.routers import agent, audit, auth, contacts, dashboard, license, personas, platform, ws
from app.security import hash_password

logger = logging.getLogger("uvicorn.error")

TAGS = [
    {"name": "auth", "description": "注册/登录"},
    {"name": "license", "description": "卡密授权"},
    {"name": "contacts", "description": "联系人画像"},
    {"name": "personas", "description": "人设"},
    {"name": "agent", "description": "AI 回复建议与自动化规则"},
    {"name": "audit", "description": "审计日志"},
    {"name": "dashboard", "description": "管理看板"},
    {"name": "platform", "description": "Android 端上报"},
]


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    # 首次启动 seed 管理员；未显式设置 ADMIN_PASSWORD 时生成随机密码并打印一次
    db = SessionLocal()
    try:
        if db.execute(select(User)).first() is None:
            password = settings.ADMIN_PASSWORD or secrets.token_urlsafe(12)
            db.add(
                User(
                    username=settings.ADMIN_USERNAME,
                    password_hash=hash_password(password),
                    role="admin",
                )
            )
            db.commit()
            if not settings.ADMIN_PASSWORD:
                logger.warning(
                    "首次启动已创建管理员账号：%s / %s （请立即登录修改密码）",
                    settings.ADMIN_USERNAME,
                    password,
                )
    finally:
        db.close()
    yield


docs_url = "/docs" if settings.ENV != "production" else None
app = FastAPI(
    title="AI Social Agent",
    version="1.0.0",
    lifespan=lifespan,
    openapi_tags=TAGS,
    docs_url=docs_url,
    redoc_url=None,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api/v1")
app.include_router(license.router, prefix="/api/v1")
app.include_router(contacts.router, prefix="/api/v1")
app.include_router(personas.router, prefix="/api/v1")
app.include_router(agent.router, prefix="/api/v1")
app.include_router(audit.router, prefix="/api/v1")
app.include_router(dashboard.router, prefix="/api/v1")
app.include_router(platform.router, prefix="/api/v1")
app.include_router(ws.router, prefix="/api/v1")


@app.get("/health")
def health():
    return {"status": "ok"}
