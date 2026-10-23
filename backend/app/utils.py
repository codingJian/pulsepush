import secrets
import string
from datetime import datetime, timezone


def ok(data=None, message: str = "ok"):
    return {"code": 200, "message": message, "data": data}


def generate_msg_id() -> str:
    now = datetime.now(timezone.utc)
    suffix = "".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(6))
    return f"msg_{now.strftime('%Y%m%d_%H%M%S')}_{suffix}"


def generate_channel_token() -> str:
    return "sec_chk_" + secrets.token_urlsafe(32)


# level -> Android Notification Channel id (client must create these exact ids)
LEVEL_TO_ANDROID_CHANNEL = {
    "critical": "critical_channel",
    "warning": "default_channel",
    "info": "default_channel",
    "silent": "silent_channel",
}

VALID_LEVELS = set(LEVEL_TO_ANDROID_CHANNEL.keys())


def normalize_level(level: str | None) -> str:
    if not level:
        return "info"
    lv = str(level).strip().lower()
    return lv if lv in VALID_LEVELS else "info"


def android_channel_for_level(level: str) -> str:
    return LEVEL_TO_ANDROID_CHANNEL[normalize_level(level)]
