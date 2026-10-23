import json

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..db import get_db
from ..models import Channel, MessageLog, UserDevice
from ..services import fcm as fcm_service
from ..services.payload_parser import parse_payload
from ..services.rate_limit import rate_limiter
from ..utils import android_channel_for_level, generate_msg_id, normalize_level

router = APIRouter(prefix="/api/v1/push", tags=["push"])

TITLE_MAX = 100
BODY_MAX = 300
URL_MAX = 256


@router.post("/{channel_token}")
async def receive_webhook(channel_token: str, request: Request,
                          db: AsyncSession = Depends(get_db)):
    # Rate limit: 60/min per channel_token, in-memory sliding window
    if not rate_limiter.allow(f"push:{channel_token}"):
        raise HTTPException(status_code=429, detail="Rate limit exceeded: 60/min per channel")

    q = await db.execute(select(Channel).where(Channel.channel_token == channel_token))
    channel = q.scalar_one_or_none()
    if not channel:
        raise HTTPException(status_code=404, detail="Channel not found")

    raw_body = await request.body()
    content_type = request.headers.get("content-type")
    parsed = parse_payload(raw_body, content_type)
    level = normalize_level(parsed.level)
    android_channel = android_channel_for_level(level)
    msg_id = generate_msg_id()

    raw_text = raw_body.decode("utf-8", errors="replace") if raw_body else ""
    if parsed.extra_data:
        try:
            raw_text = raw_text or json.dumps(parsed.extra_data, ensure_ascii=False)
        except Exception:
            pass

    # Store the FULL payload first; FCM only carries a truncated preview + msg_id.
    log = MessageLog(
        user_id=channel.user_id, channel_id=channel.id, msg_id=msg_id,
        title=parsed.title, body=parsed.body or "", url=parsed.url, level=level,
        raw_payload=raw_text, dispatched_devices_count=0,
    )
    db.add(log)
    await db.commit()

    dev_q = await db.execute(
        select(UserDevice).where(
            UserDevice.user_id == channel.user_id, UserDevice.is_active == True  # noqa: E712
        )
    )
    devices = dev_q.scalars().all()
    # Deduplicate tokens
    tokens: list[str] = []
    token_to_devices: dict[str, list[UserDevice]] = {}
    for d in devices:
        if d.fcm_token not in token_to_devices:
            tokens.append(d.fcm_token)
            token_to_devices[d.fcm_token] = []
        token_to_devices[d.fcm_token].append(d)

    notif_title = (parsed.title or "Webhook 提醒")[:TITLE_MAX]
    notif_body = (parsed.body or "")[:BODY_MAX]
    data = {
        "msg_id": msg_id,
        "level": level,
        "url": (parsed.url or "")[:URL_MAX],
        "channel_name": (channel.channel_name or "")[:64],
        "android_channel_id": android_channel,
    }
    dispatched, invalid, dry_run = fcm_service.send_multicast(
        tokens, notif_title, notif_body, data, android_channel, level=level
    )

    # Self-healing: deactivate invalid tokens
    if invalid:
        for token, reason in invalid:
            for d in token_to_devices.get(token, []):
                d.is_active = False
                d.invalid_reason = reason
    log.dispatched_devices_count = dispatched
    await db.commit()

    return {
        "code": 200,
        "message": "Webhook processed successfully",
        "dispatched_count": dispatched,
        "msg_id": msg_id,
        "dry_run": dry_run,
        "data": {"msg_id": msg_id, "dispatched_count": dispatched, "dry_run": dry_run},
    }
