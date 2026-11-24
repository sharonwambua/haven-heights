"""Push notifications through Firebase Cloud Messaging.

Disabled unless FIREBASE_CREDENTIALS_FILE is set; in that case messages are only logged,
so the rest of the app behaves the same in development.
Messages are data-only so the Android app builds every notification itself.
"""
import logging

from fastapi import BackgroundTasks
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import settings
from ..database import SessionLocal
from ..models import DeviceToken, Notification, Tenancy, User

log = logging.getLogger("haven.push")
_app = None
_tried = False


def _firebase():
    global _app, _tried
    if _tried:
        return _app
    _tried = True
    if not settings.firebase_credentials_file:
        log.info("Push disabled: FIREBASE_CREDENTIALS_FILE is not set.")
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials

        _app = firebase_admin.initialize_app(credentials.Certificate(settings.firebase_credentials_file))
    except Exception:  # noqa: BLE001
        log.exception("Could not start Firebase; push stays off.")
    return _app


def tenant_user_ids(db: Session) -> list[int]:
    return list(db.scalars(select(Tenancy.tenant_id).where(Tenancy.active.is_(True))))


def landlord_user_ids(db: Session) -> list[int]:
    return list(db.scalars(select(User.id).where(User.role == "LANDLORD")))


def notify(background: BackgroundTasks, db: Session, user_ids: list[int], title: str, body: str, kind: str) -> None:
    if not user_ids:
        return
    for uid in set(user_ids):   # the in-app inbox works even when push is off
        db.add(Notification(user_id=uid, title=title[:120], body=body, kind=kind))
    db.commit()
    tokens = list(db.scalars(select(DeviceToken.token).where(DeviceToken.user_id.in_(user_ids))))
    if tokens:
        background.add_task(_send, tokens, title, body, kind)


def _send(tokens: list[str], title: str, body: str, kind: str) -> None:
    app = _firebase()
    if app is None:
        log.info("Would push to %d device(s): %s - %s", len(tokens), title, body)
        return
    from firebase_admin import messaging

    message = messaging.MulticastMessage(
        tokens=tokens,
        data={"title": title[:100], "body": body[:200], "type": kind},
        android=messaging.AndroidConfig(priority="high"),
    )
    try:
        result = messaging.send_each_for_multicast(message, app=app)
    except Exception:  # noqa: BLE001
        log.exception("Push send failed")
        return
    dead = [t for t, r in zip(tokens, result.responses)
            if not r.success and isinstance(r.exception, (messaging.UnregisteredError, messaging.SenderIdMismatchError))]
    if dead:
        with SessionLocal() as db:
            for row in db.scalars(select(DeviceToken).where(DeviceToken.token.in_(dead))):
                db.delete(row)
            db.commit()
