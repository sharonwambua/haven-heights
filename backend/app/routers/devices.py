from fastapi import APIRouter, Depends, Response
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import current_user
from ..models import DeviceToken, User
from ..schemas import DeviceIn

router = APIRouter(prefix="/devices", tags=["devices"])


@router.post("", status_code=204)
def register_device(body: DeviceIn, db: Session = Depends(get_db), user: User = Depends(current_user)):
    """Register this phone for push. A token belongs to whoever signed in last."""
    row = db.scalar(select(DeviceToken).where(DeviceToken.token == body.token))
    if row is None:
        db.add(DeviceToken(user_id=user.id, token=body.token))
    else:
        row.user_id = user.id
    db.commit()
    return Response(status_code=204)


@router.post("/unregister", status_code=204)
def unregister_device(body: DeviceIn, db: Session = Depends(get_db), user: User = Depends(current_user)):
    row = db.scalar(select(DeviceToken).where(DeviceToken.token == body.token, DeviceToken.user_id == user.id))
    if row is not None:
        db.delete(row)
        db.commit()
    return Response(status_code=204)
