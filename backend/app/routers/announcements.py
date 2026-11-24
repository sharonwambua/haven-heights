from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Response
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import current_user, landlord_only
from ..models import Announcement, User
from ..schemas import AnnouncementCreate, AnnouncementOut
from ..services import push

router = APIRouter(prefix="/announcements", tags=["announcements"])


def _out(a: Announcement) -> AnnouncementOut:
    return AnnouncementOut(id=a.id, title=a.title, body=a.body, category=a.category,
                           created_at=a.created_at)


@router.get("", response_model=list[AnnouncementOut])
def list_announcements(limit: int = 50, db: Session = Depends(get_db), _: User = Depends(current_user)):
    rows = db.scalars(select(Announcement).order_by(Announcement.id.desc()).limit(min(limit, 100))).all()
    return [_out(a) for a in rows]


@router.post("", response_model=AnnouncementOut, status_code=201)
def post_announcement(body: AnnouncementCreate, background: BackgroundTasks, db: Session = Depends(get_db),
                      landlord: User = Depends(landlord_only)):
    item = Announcement(title=body.title.strip(), body=body.body.strip(),
                        category=body.category.value, author_id=landlord.id)
    db.add(item)
    db.commit()
    push.notify(background, db, push.tenant_user_ids(db), item.title, item.body, "notice")
    return _out(item)


@router.delete("/{announcement_id}", status_code=204)
def delete_announcement(announcement_id: int, db: Session = Depends(get_db),
                        _: User = Depends(landlord_only)):
    item = db.get(Announcement, announcement_id)
    if item is None:
        raise HTTPException(404, "Notice not found.")
    db.delete(item)
    db.commit()
    return Response(status_code=204)
