from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    email: str = Field(min_length=3, max_length=128)
    password: str = Field(min_length=6, max_length=128)


class LoginRequest(BaseModel):
    # Accept a username or an email in the same field.
    username: str
    password: str


class DeviceBindRequest(BaseModel):
    device_id: str
    fcm_token: str
    device_name: Optional[str] = None
    # PRD 3.2 used device_model; accept it as an alias.
    device_model: Optional[str] = None
    platform: str = "android"
    app_version: Optional[str] = None

    def resolved_device_name(self) -> Optional[str]:
        return self.device_name or self.device_model


class DeviceUnbindRequest(BaseModel):
    device_id: str


class ChannelCreateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    channel_name: Optional[str] = None
    name: Optional[str] = None
    description: Optional[str] = None

    def resolved_name(self) -> str:
        return (self.channel_name or self.name or "default").strip()


class StandardWebhookPayload(BaseModel):
    title: Optional[str] = "Webhook 提醒"
    body: Optional[str] = None
    url: Optional[str] = None
    level: Optional[str] = "info"
    extra_data: Optional[dict[str, Any]] = None
