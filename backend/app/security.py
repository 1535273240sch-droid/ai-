"""安全模块：pbkdf2 密码哈希 + JWT 签发/校验。

JWT 优先使用 pyjwt；若 pyjwt 未安装则退化为标准库 HMAC 实现，保证 Windows 环境可用。
"""
import base64
import hashlib
import hmac
import json
import os
import secrets
import time
from datetime import datetime, timedelta, timezone
from typing import Any

from app.config import settings

# ---------------- 密码：pbkdf2_hmac 加盐 ----------------
_ALGO = "pbkdf2_sha256"


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, settings.PASSWORD_ITERATIONS
    )
    return f"{_ALGO}${settings.PASSWORD_ITERATIONS}${salt.hex()}${dk.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        algo, iterations, salt_hex, hash_hex = stored.split("$")
        if algo != _ALGO:
            return False
        dk = hashlib.pbkdf2_hmac(
            "sha256",
            password.encode("utf-8"),
            bytes.fromhex(salt_hex),
            int(iterations),
        )
        return hmac.compare_digest(dk.hex(), hash_hex)
    except (ValueError, AttributeError):
        return False


# ---------------- JWT ----------------
try:  # 优先使用 pyjwt
    import jwt as _pyjwt  # type: ignore[no-redef]
except ImportError:  # pragma: no cover - 仅当 pyjwt 缺失时走标准库实现
    _pyjwt = None


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _b64url_decode(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + padding)


def _jwt_signature(message: str, secret: str) -> str:
    return _b64url_encode(
        hmac.new(secret.encode("utf-8"), message.encode("utf-8"), hashlib.sha256).digest()
    )


def _jwt_create(payload: dict[str, Any], secret: str) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    header_b64 = _b64url_encode(json.dumps(header, separators=(",", ":")).encode("utf-8"))
    payload_b64 = _b64url_encode(
        json.dumps(payload, separators=(",", ":"), default=str).encode("utf-8")
    )
    signing_input = f"{header_b64}.{payload_b64}"
    signature = _jwt_signature(signing_input, secret)
    return f"{signing_input}.{signature}"


def _jwt_decode(token: str, secret: str) -> dict[str, Any]:
    try:
        header_b64, payload_b64, signature = token.split(".")
    except ValueError:
        raise ValueError("token 格式错误")
    signing_input = f"{header_b64}.{payload_b64}"
    if not hmac.compare_digest(
        _jwt_signature(signing_input, secret), signature
    ):
        raise ValueError("签名校验失败")
    payload = json.loads(_b64url_decode(payload_b64))
    exp = payload.get("exp")
    if exp is not None and time.time() > exp:
        raise ValueError("token 已过期")
    return payload


def create_access_token(user_id: int, username: str, role: str) -> str:
    now = datetime.now(timezone.utc)
    payload: dict[str, Any] = {
        "sub": str(user_id),
        "username": username,
        "role": role,
        "jti": secrets.token_hex(16),  # 令牌唯一标识，便于未来吊销
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(minutes=settings.TOKEN_EXPIRE_MINUTES)).timestamp()),
    }
    if _pyjwt is not None:
        return _pyjwt.encode(payload, settings.SECRET_KEY, algorithm="HS256")
    return _jwt_create(payload, settings.SECRET_KEY)


def decode_token(token: str) -> dict[str, Any]:
    """解析并校验 JWT，失败抛 ValueError。"""
    if _pyjwt is not None:
        return _pyjwt.decode(token, settings.SECRET_KEY, algorithms=["HS256"])
    return _jwt_decode(token, settings.SECRET_KEY)
