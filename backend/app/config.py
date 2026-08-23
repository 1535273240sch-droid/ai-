"""应用配置：支持环境变量覆盖，可在项目根目录放置 .env 文件。"""
from functools import lru_cache

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    APP_NAME: str = "AI Social Agent"
    # development / production；production 下强制安全默认值检查
    ENV: str = "development"

    # ---- 认证 ----
    # 生产环境必须通过环境变量注入，禁止使用默认值
    SECRET_KEY: str = "dev-secret-key-not-for-production"
    TOKEN_EXPIRE_MINUTES: int = 60 * 8  # 8 小时
    # pbkdf2 迭代次数
    PASSWORD_ITERATIONS: int = 200_000

    # ---- 数据库 ----
    DATABASE_URL: str = "sqlite:///./ai_social_agent.db"

    # ---- 默认管理员（首次启动 seed；ADMIN_PASSWORD 留空则生成随机密码打印一次）----
    ADMIN_USERNAME: str = "admin"
    ADMIN_PASSWORD: str = ""

    # ---- CORS 白名单（逗号分隔；开发默认 *）----
    CORS_ORIGINS: str = "*"

    # ---- Model Gateway（OpenAI 兼容）----
    OPENAI_BASE_URL: str = "https://api.openai.com/v1"
    OPENAI_API_KEY: str = ""
    OPENAI_MODEL: str = "gpt-4o-mini"
    OPENAI_TIMEOUT_SECONDS: float = 20.0

    # ---- 节奏调度 ----
    DELAY_MIN_MS: int = 800
    DELAY_MAX_MS: int = 15000

    # ---- 记忆 ----
    MEMORY_SHORT_WINDOW: int = 10
    MEMORY_SUMMARY_MAX_CHARS: int = 500

    # ---- 敏感词（half 模式命中即转人工）----
    SENSITIVE_KEYWORDS: str = "转账,借钱,银行卡,身份证,验证码,密码,裸聊,赌博,彩票,投资,贷款,汇款"

    # ---- 限流 ----
    LOGIN_MAX_FAILURES: int = 5          # 登录失败锁定阈值
    LOGIN_LOCK_MINUTES: int = 15         # 锁定窗口
    ACTIVATE_RATE_LIMIT: int = 20        # 激活按 IP 每 15 分钟上限
    SUGGEST_DAILY_QUOTA: int = 2000      # /agent/suggest 每用户每日配额

    @property
    def sensitive_keyword_list(self) -> list[str]:
        return [k.strip() for k in self.SENSITIVE_KEYWORDS.split(",") if k.strip()]

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]

    @model_validator(mode="after")
    def _check_production_defaults(self) -> "Settings":
        if self.ENV == "production":
            if self.SECRET_KEY == "dev-secret-key-not-for-production":
                raise ValueError("生产环境必须通过环境变量 SECRET_KEY 注入，禁止使用默认密钥")
            if not self.ADMIN_PASSWORD:
                raise ValueError("生产环境必须设置 ADMIN_PASSWORD 环境变量")
            if "*" in self.cors_origin_list:
                raise ValueError("生产环境 CORS_ORIGINS 必须为白名单，禁止 '*'")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
