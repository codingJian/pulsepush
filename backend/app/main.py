import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .db import init_db
from .routers import auth, channels, devices, messages, push
from .services.fcm import is_dry_run

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    logger.info("DB ready. FCM dry_run=%s", is_dry_run())
    yield


app = FastAPI(title="Webhook Push Gateway", version="1.0.0", lifespan=lifespan)

app.include_router(auth.router)
app.include_router(devices.router)
app.include_router(channels.router)
app.include_router(push.router)
app.include_router(messages.router)


@app.get("/health")
async def health():
    return {"code": 200, "message": "ok", "data": {"status": "healthy", "fcm_dry_run": is_dry_run()}}


@app.get("/")
async def root():
    return {"code": 200, "message": "Webhook Push Gateway", "data": {"docs": "/docs", "health": "/health"}}
