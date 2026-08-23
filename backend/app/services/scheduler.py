"""节奏调度：按消息长度/复杂度计算自然延迟，避免秒回。"""
from app.config import settings


def compute_delay_ms(message: str) -> int:
    length = len(message)
    if length <= 0:
        return settings.DELAY_MIN_MS
    # 消息越长，读+想的时间越长；clip 到 [min, max]
    raw = settings.DELAY_MIN_MS + length * 40
    return max(settings.DELAY_MIN_MS, min(settings.DELAY_MAX_MS, raw))
