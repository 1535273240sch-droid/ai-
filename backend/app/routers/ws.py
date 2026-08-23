"""WebSocket：/ws?token=<jwt>，推送建议 / Kill Switch 等事件。连接时校验卡密有效性。"""
from datetime import datetime, timezone

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from sqlalchemy import select

from app.db import SessionLocal
from app.models import License
from app.security import decode_token

router = APIRouter(tags=["ws"])


class ConnectionManager:
    def __init__(self) -> None:
        self.active: dict[int, list[WebSocket]] = {}

    async def connect(self, user_id: int, ws: WebSocket) -> None:
        await ws.accept()
        self.active.setdefault(user_id, []).append(ws)

    def disconnect(self, user_id: int, ws: WebSocket) -> None:
        conns = self.active.get(user_id, [])
        if ws in conns:
            conns.remove(ws)
        if not conns:
            self.active.pop(user_id, None)

    async def broadcast(self, user_id: int, payload: dict) -> None:
        for ws in list(self.active.get(user_id, [])):
            try:
                await ws.send_json(payload)
            except Exception:
                self.disconnect(user_id, ws)


manager = ConnectionManager()


def _license_active(user_id: int) -> bool:
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    db = SessionLocal()
    try:
        row = db.execute(
            select(License).where(
                License.user_id == user_id,
                License.status == "active",
                License.expires_at > now,
            )
        ).scalars().first()
        return row is not None
    finally:
        db.close()


@router.websocket("/ws")
async def ws_endpoint(ws: WebSocket, token: str = ""):
    try:
        payload = decode_token(token)
        user_id = int(payload["sub"])
    except Exception:
        await ws.close(code=4401)
        return
    if not _license_active(user_id):
        await ws.close(code=4403)  # 无有效卡密，拒绝推送
        return
    await manager.connect(user_id, ws)
    try:
        while True:
            await ws.receive_text()  # 保持连接，客户端可发 ping
    except WebSocketDisconnect:
        manager.disconnect(user_id, ws)
