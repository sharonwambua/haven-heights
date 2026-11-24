from fastapi import APIRouter, Depends, Response
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import current_user
from ..models import Notification, User
from ..schemas import NotificationOut, NotificationsOut

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.get("", response_model=NotificationsOut)
def inbox(limit: int = 50, db: Session = Depends(get_db), user: User = Depends(current_user)):
    rows = db.scalars(select(Notification).where(Notification.user_id == user.id)
                      .order_by(Notification.id.desc()).limit(min(limit, 100))).all()
    unread = db.scalar(select(func.count()).select_from(Notification)
                       .where(Notification.user_id == user.id, Notification.read.is_(False))) or 0
    return NotificationsOut(unread=unread, items=[
        NotificationOut(id=n.id, title=n.title, body=n.body, kind=n.kind, read=n.read, created_at=n.created_at)
        for n in rows])


@router.post("/read", status_code=204)
def mark_all_read(db: Session = Depends(get_db), user: User = Depends(current_user)):
    db.execute(update(Notification).where(Notification.user_id == user.id, Notification.read.is_(False)).values(read=True))
    db.commit()
    return Response(status_code=204)
