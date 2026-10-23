from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from ..db import get_db
from ..models import MessageLog, User
from ..security import get_current_user
from ..utils import ok

router = APIRouter(prefix="/api/v1/messages", tags=["messages"])


def _msg_out(m: MessageLog, full: bool = False):
    out = {
        "id": m.id, "msg_id": m.msg_id, "channel_id": m.channel_id,
        "title": m.title, "body": m.body, "url": m.url, "level": m.level,
        "dispatched_devices_count": m.dispatched_devices_count,
        "is_read": m.is_read,
        "created_at": m.created_at.isoformat() if m.created_at else None,
    }
    if full:
        out["raw_payload"] = m.raw_payload
    return out


@router.get("")
async def list_messages(limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0),
                        db: AsyncSession = Depends(get_db),
                        current_user: User = Depends(get_current_user)):
    total_q = await db.execute(
        select(func.count(MessageLog.id)).where(MessageLog.user_id == current_user.id)
    )
    total = total_q.scalar() or 0
    q = await db.execute(
        select(MessageLog).where(MessageLog.user_id == current_user.id)
        .order_by(MessageLog.created_at.desc(), MessageLog.id.desc())
        .limit(limit).offset(offset)
    )
    items = [_msg_out(m) for m in q.scalars().all()]
    return ok({"total": total, "limit": limit, "offset": offset, "items": items})


@router.get("/{msg_id}")
async def get_message(msg_id: str, db: AsyncSession = Depends(get_db),
                      current_user: User = Depends(get_current_user)):
    q = await db.execute(
        select(MessageLog).where(MessageLog.msg_id == msg_id, MessageLog.user_id == current_user.id)
    )
    m = q.scalar_one_or_none()
    if not m:
        raise HTTPException(status_code=404, detail="Message not found")
    return ok(_msg_out(m, full=True))


@router.post("/{msg_id}/read")
async def mark_read(msg_id: str, db: AsyncSession = Depends(get_db),
                    current_user: User = Depends(get_current_user)):
    q = await db.execute(
        select(MessageLog).where(MessageLog.msg_id == msg_id, MessageLog.user_id == current_user.id)
    )
    m = q.scalar_one_or_none()
    if not m:
        raise HTTPException(status_code=404, detail="Message not found")
    m.is_read = True
    await db.commit()
    return ok(_msg_out(m), message="Marked as read")
