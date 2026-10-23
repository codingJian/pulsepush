from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..db import get_db
from ..models import User, UserDevice
from ..schemas import DeviceBindRequest, DeviceUnbindRequest
from ..security import get_current_user
from ..utils import ok

router = APIRouter(prefix="/api/v1/devices", tags=["devices"])


def _device_out(d: UserDevice):
    return {
        "id": d.id, "user_id": d.user_id, "device_id": d.device_id,
        "device_name": d.device_name, "platform": d.platform,
        "app_version": d.app_version, "is_active": d.is_active,
        "invalid_reason": d.invalid_reason,
        "last_seen_at": d.last_seen_at.isoformat() if d.last_seen_at else None,
        "created_at": d.created_at.isoformat() if d.created_at else None,
    }


@router.post("/bind")
async def bind_device(payload: DeviceBindRequest, db: AsyncSession = Depends(get_db),
                      current_user: User = Depends(get_current_user)):
    now = datetime.now(timezone.utc)
    # 1) If this fcm_token is held by a *different* device_id, rebind it:
    #    deactivate the conflicting rows so the token only pushes once.
    conflicts = await db.execute(
        select(UserDevice).where(
            UserDevice.fcm_token == payload.fcm_token,
            UserDevice.device_id != payload.device_id,
        )
    )
    for row in conflicts.scalars().all():
        row.is_active = False
        row.invalid_reason = "TOKEN_REASSIGNED"

    # 2) Same device_id anywhere (even under another user) -> move/update it.
    existing_q = await db.execute(
        select(UserDevice).where(UserDevice.device_id == payload.device_id)
    )
    device = existing_q.scalars().first()
    if device is not None:
        device.user_id = current_user.id
        device.fcm_token = payload.fcm_token
        device.device_name = payload.resolved_device_name() or device.device_name
        device.platform = payload.platform or device.platform
        device.app_version = payload.app_version or device.app_version
        device.is_active = True
        device.invalid_reason = None
        device.last_seen_at = now
    else:
        device = UserDevice(
            user_id=current_user.id, device_id=payload.device_id,
            fcm_token=payload.fcm_token, device_name=payload.resolved_device_name(),
            platform=payload.platform or "android", app_version=payload.app_version,
            is_active=True, last_seen_at=now,
        )
        db.add(device)
    await db.commit()

    total_q = await db.execute(
        select(UserDevice).where(UserDevice.user_id == current_user.id, UserDevice.is_active == True)  # noqa: E712
    )
    total = len(total_q.scalars().all())
    return ok({"bound_devices_total": total, "device": _device_out(device)},
              message="Device successfully registered and active")


@router.post("/unbind")
async def unbind_device(payload: DeviceUnbindRequest, db: AsyncSession = Depends(get_db),
                        current_user: User = Depends(get_current_user)):
    q = await db.execute(
        select(UserDevice).where(
            UserDevice.user_id == current_user.id, UserDevice.device_id == payload.device_id
        )
    )
    device = q.scalar_one_or_none()
    if device:
        device.is_active = False
        device.invalid_reason = "UNBOUND"
        await db.commit()
    return ok({"device_id": payload.device_id}, message="Device unbound")


@router.get("")
async def list_devices(db: AsyncSession = Depends(get_db),
                       current_user: User = Depends(get_current_user)):
    q = await db.execute(
        select(UserDevice).where(UserDevice.user_id == current_user.id)
        .order_by(UserDevice.created_at.desc())
    )
    devices = q.scalars().all()
    return ok([_device_out(d) for d in devices])
