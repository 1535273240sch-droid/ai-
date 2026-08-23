"""Pydantic v2 请求/响应模型，字段命名严格对齐 API_CONTRACT.md。"""
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


# ---------------- auth ----------------
class RegisterRequest(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=6, max_length=128)


class LoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    role: str


class AuthResponse(BaseModel):
    token: str
    user: UserOut


class MeResponse(BaseModel):
    user: UserOut


# ---------------- license ----------------
class ActivateRequest(BaseModel):
    code: str
    device_fingerprint: str = Field(min_length=4, max_length=256)


class DeactivateRequest(BaseModel):
    device_fingerprint: str


class LicenseOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    code: str
    expires_at: datetime
    features: dict[str, Any]
    status: str


class ActivateResponse(BaseModel):
    activated: bool
    license: LicenseOut


class LicenseInfoResponse(BaseModel):
    valid: bool
    license: LicenseOut | None = None


class AdminLicenseCreateRequest(BaseModel):
    count: int = Field(ge=1, le=100)
    days: int = Field(ge=1, le=3650)
    features: dict[str, Any] = Field(default_factory=dict)


class AdminLicenseCreateResponse(BaseModel):
    codes: list[str]


class AdminLicenseItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    code: str
    status: str
    activated_by: str | None = None
    device_fingerprint: str | None = None
    expires_at: datetime
    features: dict[str, Any]
    created_at: datetime


class AdminLicenseListResponse(BaseModel):
    items: list[AdminLicenseItem]
    total: int


class OkResponse(BaseModel):
    ok: bool = True


# ---------------- contacts ----------------
class ContactProfile(BaseModel):
    relationship: str | None = None
    interaction_style: str | None = None
    reply_frequency: str | None = None
    sentence_style: str | None = None
    taboos: list[str] = Field(default_factory=list)


class ContactCreateRequest(BaseModel):
    platform: str = Field(min_length=1, max_length=32)
    platform_contact_id: str = Field(min_length=1, max_length=128)
    nickname: str = Field(min_length=1, max_length=128)
    profile: ContactProfile = Field(default_factory=ContactProfile)


class ContactUpdateRequest(BaseModel):
    platform: str | None = None
    platform_contact_id: str | None = None
    nickname: str | None = None
    profile: ContactProfile | None = None


class ContactOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    platform: str
    platform_contact_id: str
    nickname: str
    profile: dict[str, Any]
    created_at: datetime


class ContactListResponse(BaseModel):
    items: list[ContactOut]


# ---------------- personas ----------------
class PersonaCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    config: dict[str, Any] = Field(default_factory=dict)
    is_default: bool = False
    is_global: bool = False


class PersonaUpdateRequest(BaseModel):
    name: str | None = None
    config: dict[str, Any] | None = None
    is_default: bool | None = None
    is_global: bool | None = None


class PersonaOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    config: dict[str, Any]
    is_default: bool
    is_global: bool


class PersonaListResponse(BaseModel):
    items: list[PersonaOut]


# ---------------- agent ----------------
AgentMode = Literal["suggest", "auto", "half"]


class SuggestRequest(BaseModel):
    contact_id: int
    message: str = Field(min_length=1, max_length=4000)
    mode: AgentMode = "suggest"


class DecisionOut(BaseModel):
    mode: str
    reason: str


class SuggestResponse(BaseModel):
    suggestions: list[str]
    decision: DecisionOut
    delay_ms: int


class RuleCreateRequest(BaseModel):
    contact_id: int | None = None
    mode: Literal["suggest", "auto", "manual"] = "manual"
    keywords: list[str] = Field(min_length=1)
    active: bool = True


class RuleUpdateRequest(BaseModel):
    contact_id: int | None = None
    mode: Literal["suggest", "auto", "manual"] | None = None
    keywords: list[str] | None = None
    active: bool | None = None


class RuleOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    contact_id: int | None = None
    mode: str
    keywords: list[str]
    active: bool
    created_at: datetime


class RuleListResponse(BaseModel):
    items: list[RuleOut]


# ---------------- audit ----------------
class AuditLogOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    event: str
    payload: dict[str, Any]
    created_at: datetime


class AuditLogListResponse(BaseModel):
    items: list[AuditLogOut]
    total: int


# ---------------- dashboard ----------------
class StatsResponse(BaseModel):
    message_count: int
    reply_count: int
    active_contacts: int
    persona_count: int
    license_count: int
    active_license_count: int
    recent_logs: list[AuditLogOut]


# ---------------- platform ----------------
PlatformEventType = Literal["message_received", "message_sent", "online", "offline"]


class PlatformEventRequest(BaseModel):
    platform: str = Field(min_length=1, max_length=32)
    platform_contact_id: str = Field(min_length=1, max_length=128)
    type: PlatformEventType
    content: str | None = None
    timestamp: datetime | None = None


class PlatformReplyRequest(BaseModel):
    platform: str = Field(min_length=1, max_length=32)
    platform_contact_id: str = Field(min_length=1, max_length=128)
    content: str = Field(min_length=1, max_length=4000)
