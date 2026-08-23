"""联系人记忆：短期上下文（最近 N 条）与简单长期摘要。"""
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.config import settings
from app.models import MemoryEntry


def add_message(db: Session, contact_id: int, role: str, content: str) -> None:
    """只入队不提交，由调用方统一 commit（事务收敛）。"""
    db.add(MemoryEntry(contact_id=contact_id, role=role, content=content))


def get_recent(db: Session, contact_id: int, limit: int | None = None) -> list[dict[str, str]]:
    limit = limit or settings.MEMORY_SHORT_WINDOW
    rows = db.execute(
        select(MemoryEntry)
        .where(MemoryEntry.contact_id == contact_id)
        .order_by(MemoryEntry.id.desc())
        .limit(limit)
    ).scalars().all()
    return [{"role": r.role, "content": r.content} for r in reversed(rows)]


def summarize_long_term(db: Session, contact_id: int) -> str:
    """长期摘要：count 总数 + 最早一条开头，避免全表载入（审查加固）。"""
    total = db.execute(
        select(func.count(MemoryEntry.id)).where(MemoryEntry.contact_id == contact_id)
    ).scalar_one()
    if total == 0:
        return ""
    head = db.execute(
        select(MemoryEntry.content)
        .where(MemoryEntry.contact_id == contact_id)
        .order_by(MemoryEntry.id.asc())
        .limit(1)
    ).scalar_one()
    return f"共 {total} 轮历史对话；最早话题：{head[: settings.MEMORY_SUMMARY_MAX_CHARS]}"
