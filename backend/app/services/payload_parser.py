import json
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import parse_qs


@dataclass
class ParsedPayload:
    title: str = "Webhook 提醒"
    body: str = ""
    url: str | None = None
    level: str = "info"
    extra_data: dict | None = None


def _flatten(obj: Any, prefix: str = "", lines: list[str] | None = None) -> list[str]:
    if lines is None:
        lines = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            _flatten(v, key, lines)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            key = f"{prefix}[{i}]"
            _flatten(v, key, lines)
    else:
        lines.append(f"- **{prefix}**: {obj}")
    return lines


def _from_dict(data: dict) -> ParsedPayload:
    standard_keys = {"title", "body", "content", "message", "text", "url", "level"}
    if standard_keys & set(data.keys()):
        title = data.get("title") or "Webhook 提醒"
        body = data.get("body") or data.get("content") or data.get("message") or data.get("text")
        extra = {k: v for k, v in data.items() if k not in standard_keys and k != "extra_data"}
        if not body:
            # Standard-ish payload without a body field: beautify the rest.
            body = "\n".join(_flatten(extra)) if extra else ""
        merged_extra = None
        if isinstance(data.get("extra_data"), dict):
            merged_extra = dict(data["extra_data"])
        if extra:
            merged_extra = {**(merged_extra or {}), **extra}
        return ParsedPayload(
            title=str(title),
            body=str(body or ""),
            url=str(data["url"]) if data.get("url") else None,
            level=str(data.get("level") or "info"),
            extra_data=merged_extra,
        )
    # Unstructured JSON: recursively beautify into body
    lines = _flatten(data)
    body = "\n".join(lines)
    first_key = next(iter(data.keys()), "Webhook")
    title = str(first_key)[:30] if first_key else body[:30]
    return ParsedPayload(title=title, body=body, level="info", extra_data=data)


def parse_payload(raw_body: bytes, content_type: str | None) -> ParsedPayload:
    ct = (content_type or "").lower()
    text = raw_body.decode("utf-8", errors="replace") if raw_body else ""

    # Form mode
    if "application/x-www-form-urlencoded" in ct:
        parsed = parse_qs(text)
        flat = {k: v[0] if len(v) == 1 else v for k, v in parsed.items()}
        return _from_dict(flat)

    # JSON mode (by content-type or by sniffing)
    stripped = text.strip()
    if "application/json" in ct or stripped.startswith("{") or stripped.startswith("["):
        try:
            data = json.loads(text)
            if isinstance(data, dict):
                return _from_dict(data)
            body = "\n".join(_flatten(data))
            return ParsedPayload(title=body[:30] or "Webhook 提醒", body=body, extra_data={"value": data})
        except Exception:
            pass  # fall through to plain text

    # Plain text mode
    body = text
    first_line = body.strip().splitlines()[0] if body.strip() else ""
    title = first_line[:20] if first_line else "Webhook 提醒"
    return ParsedPayload(title=title, body=body)
