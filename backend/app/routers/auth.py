from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from ..db import get_db
from ..models import User
from ..schemas import LoginRequest, RegisterRequest
from ..security import create_access_token, hash_password, verify_password
from ..utils import ok

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


def _user_out(u: User):
    return {"id": u.id, "username": u.username, "email": u.email, "is_active": u.is_active,
            "created_at": u.created_at.isoformat() if u.created_at else None}


@router.post("/register")
async def register(payload: RegisterRequest, db: AsyncSession = Depends(get_db)):
    existing = await db.execute(
        select(User).where(or_(User.username == payload.username, User.email == payload.email))
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Username or email already exists")
    user = User(username=payload.username, email=payload.email,
                hashed_password=hash_password(payload.password))
    db.add(user)
    await db.commit()
    await db.refresh(user)
    token = create_access_token(user.id, user.username)
    return ok({**_user_out(user), "access_token": token, "token_type": "bearer"},
              message="Registered successfully")


@router.post("/login")
async def login(payload: LoginRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(User).where(or_(User.username == payload.username, User.email == payload.username))
    )
    user = result.scalar_one_or_none()
    if not user or not verify_password(payload.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    token = create_access_token(user.id, user.username)
    return ok({**_user_out(user), "access_token": token, "token_type": "bearer"},
              message="Login successful")
