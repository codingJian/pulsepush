from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..config import get_settings
from ..db import get_db
from ..models import Channel, User
from ..schemas import ChannelCreateRequest
from ..security import get_current_user
from ..utils import generate_channel_token, ok

router = APIRouter(prefix="/api/v1/channels", tags=["channels"])


def _channel_out(c: Channel):
    base = get_settings().base_url.rstrip("/")
    return {
        "id": c.id, "user_id": c.user_id, "channel_name": c.channel_name,
        "channel_token": c.channel_token, "description": c.description,
        "webhook_url": f"{base}/api/v1/push/{c.channel_token}",
        "created_at": c.created_at.isoformat() if c.created_at else None,
    }


@router.get("")
async def list_channels(db: AsyncSession = Depends(get_db),
                        current_user: User = Depends(get_current_user)):
    q = await db.execute(
        select(Channel).where(Channel.user_id == current_user.id).order_by(Channel.created_at.desc())
    )
    return ok([_channel_out(c) for c in q.scalars().all()])


@router.post("")
async def create_channel(payload: ChannelCreateRequest, db: AsyncSession = Depends(get_db),
                         current_user: User = Depends(get_current_user)):
    channel = Channel(
        user_id=current_user.id, channel_name=payload.resolved_name(),
        channel_token=generate_channel_token(), description=payload.description,
    )
    db.add(channel)
    await db.commit()
    await db.refresh(channel)
    return ok(_channel_out(channel), message="Channel created")


@router.delete("/{channel_id}")
async def delete_channel(channel_id: int, db: AsyncSession = Depends(get_db),
                         current_user: User = Depends(get_current_user)):
    q = await db.execute(
        select(Channel).where(Channel.id == channel_id, Channel.user_id == current_user.id)
    )
    channel = q.scalar_one_or_none()
    if not channel:
        raise HTTPException(status_code=404, detail="Channel not found")
    await db.delete(channel)
    await db.commit()
    return ok({"id": channel_id}, message="Channel deleted")
