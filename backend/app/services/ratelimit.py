"""内存限流（进程内，MVP 够用）：登录失败锁定 / 激活 IP 限频 / 建议每日配额。"""
import time
from collections import defaultdict

from app.config import settings

_WINDOW = 15 * 60  # 15 分钟

_login_failures: dict[str, list[float]] = defaultdict(list)
_activate_hits: dict[str, list[float]] = defaultdict(list)
_suggest_quota: dict[int, list] = {}


def record_login_failure(key: str) -> int:
    now = time.time()
    arr = _login_failures[key]
    arr[:] = [t for t in arr if now - t < _WINDOW]
    arr.append(now)
    return len(arr)


def is_login_locked(key: str) -> bool:
    now = time.time()
    arr = [t for t in _login_failures.get(key, []) if now - t < _WINDOW]
    return len(arr) >= settings.LOGIN_MAX_FAILURES


def clear_login_failures(key: str) -> None:
    _login_failures.pop(key, None)


def check_activate_rate(ip: str) -> bool:
    now = time.time()
    arr = _activate_hits[ip]
    arr[:] = [t for t in arr if now - t < _WINDOW]
    if len(arr) >= settings.ACTIVATE_RATE_LIMIT:
        return False
    arr.append(now)
    return True


def check_suggest_quota(user_id: int) -> bool:
    today = time.strftime("%Y-%m-%d")
    rec = _suggest_quota.get(user_id)
    if rec is None or rec[0] != today:
        _suggest_quota[user_id] = [today, 0]
        rec = _suggest_quota[user_id]
    if rec[1] >= settings.SUGGEST_DAILY_QUOTA:
        return False
    rec[1] += 1
    return True
