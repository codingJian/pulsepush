import logging
import os

logger = logging.getLogger(__name__)

_firebase_app = None
_dry_run: bool | None = None


def is_dry_run() -> bool:
    global _dry_run, _firebase_app
    if _dry_run is not None:
        return _dry_run
    cred_path = os.getenv("FIREBASE_CREDENTIALS", "")
    if not cred_path or not os.path.isfile(cred_path):
        logger.warning("FIREBASE_CREDENTIALS not set / file missing -> FCM dry-run mode")
        _dry_run = True
        return True
    try:
        import firebase_admin
        from firebase_admin import credentials

        if not firebase_admin._apps:
            cred = credentials.Certificate(cred_path)
            _firebase_app = firebase_admin.initialize_app(cred)
        else:
            _firebase_app = firebase_admin.get_app()
        _dry_run = False
        logger.info("Firebase Admin initialized")
        return False
    except Exception as exc:
        logger.exception("Firebase init failed, falling back to dry-run: %s", exc)
        _dry_run = True
        return True


def reset_for_test():
    global _dry_run
    _dry_run = None


def send_multicast(tokens: list[str], title: str, body: str, data: dict[str, str],
                   android_channel_id: str, level: str = "info"):
    """
    Returns (dispatched_count, invalid: list[tuple[token, reason]], dry_run: bool)
    Title/body must already be truncated by the caller (FCM 4KB cap).
    """
    if not tokens:
        return 0, [], is_dry_run()

    # Build the message BEFORE the dry-run branch so construction errors
    # (bad kwargs etc.) surface in dry-run tests too, not only in production.
    from firebase_admin import messaging

    android_priority = {
        "critical": "max",
        "warning": "high",
        "info": "default",
        "silent": "min",
    }.get(level, "default")
    notification = messaging.Notification(title=title, body=body)
    android_config = messaging.AndroidConfig(
        priority="high",
        notification=messaging.AndroidNotification(
            channel_id=android_channel_id,
            priority=android_priority,
            default_sound=True,
            click_action="OPEN_MESSAGE_DETAIL",
        ),
        data={k: str(v) for k, v in data.items()},
    )
    message = messaging.MulticastMessage(
        tokens=tokens, notification=notification, android=android_config,
        data={k: str(v) for k, v in data.items()},
    )

    if is_dry_run():
        logger.info("[dry-run] FCM multicast to %d devices: %s - %s", len(tokens), title, body[:80])
        return len(tokens), [], True
    try:
        resp = messaging.send_each_for_multicast(message)
    except Exception as exc:
        logger.exception("FCM send failed: %s", exc)
        return 0, [], False

    invalid: list[tuple[str, str]] = []
    success = 0
    for idx, r in enumerate(resp.responses):
        if r.success:
            success += 1
        else:
            exc = r.exception
            logger.warning("FCM send failed for token #%d: %s (%s)", idx,
                           exc.__class__.__name__ if exc else "?", exc)
            reason = None
            if isinstance(exc, messaging.UnregisteredError):
                reason = "UNREGISTERED"
            elif exc is not None and exc.__class__.__name__ == "SenderIdMismatchError":
                reason = "SENDER_ID_MISMATCH"
            elif exc is not None and "registration-token-not-registered" in str(exc):
                reason = "UNREGISTERED"
            elif exc is not None and exc.__class__.__name__ == "InvalidArgumentError" \
                    and "not a valid FCM registration token" in str(exc):
                # Malformed token can never succeed; prune it too (NFR self-healing).
                reason = "INVALID_TOKEN"
            if reason and idx < len(tokens):
                invalid.append((tokens[idx], reason))
    return success, invalid, False
