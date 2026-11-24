from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import active_tenancy, current_user, landlord_only
from ..models import MaintenanceRequest, Tenancy, User
from ..schemas import RequestCreate, RequestOut, RequestStatus, StatusUpdate
from ..services import push

router = APIRouter(prefix="/requests", tags=["maintenance"])


def request_out(r: MaintenanceRequest) -> RequestOut:
    return RequestOut(
        id=r.id, tenancy_id=r.tenancy_id, tenant_name=r.tenancy.tenant.name,
        unit_number=r.tenancy.unit.unit_number, title=r.title, description=r.description,
        urgency=r.urgency, status=r.status, landlord_note=r.landlord_note,
        created_at=r.created_at, updated_at=r.updated_at,
    )


@router.post("", response_model=RequestOut, status_code=201)
def create_request(body: RequestCreate, background: BackgroundTasks, db: Session = Depends(get_db),
                   user: User = Depends(current_user)):
    if user.role == "LANDLORD":
        tenancy = db.get(Tenancy, body.tenancy_id) if body.tenancy_id else None
    else:
        tenancy = active_tenancy(db, user)
    if tenancy is None or not tenancy.active:
        raise HTTPException(404, "Active tenancy not found.")
    req = MaintenanceRequest(tenancy_id=tenancy.id, title=body.title.strip(),
                             description=body.description.strip(), urgency=body.urgency.value)
    db.add(req)
    db.commit()
    if user.role != "LANDLORD":
        push.notify(background, db, push.landlord_user_ids(db),
                    f"New {req.urgency.lower()} request from unit {tenancy.unit.unit_number}", req.title, "request")
    return request_out(req)


@router.get("", response_model=list[RequestOut])
def list_requests(status: RequestStatus | None = None, db: Session = Depends(get_db),
                  user: User = Depends(current_user)):
    query = select(MaintenanceRequest).order_by(MaintenanceRequest.created_at.desc())
    if user.role != "LANDLORD":
        tenancy = active_tenancy(db, user)
        if tenancy is None:
            return []
        query = query.where(MaintenanceRequest.tenancy_id == tenancy.id)
    if status:
        query = query.where(MaintenanceRequest.status == status.value)
    return [request_out(r) for r in db.scalars(query).all()]


@router.patch("/{request_id}/status", response_model=RequestOut)
def update_status(request_id: int, body: StatusUpdate, background: BackgroundTasks, db: Session = Depends(get_db),
                  _: User = Depends(landlord_only)):
    req = db.get(MaintenanceRequest, request_id)
    if req is None:
        raise HTTPException(404, "Request not found.")
    req.status = body.status.value
    if body.landlord_note is not None:
        req.landlord_note = body.landlord_note.strip() or None
    db.commit()
    label = {"OPEN": "open", "IN_PROGRESS": "in progress", "RESOLVED": "resolved"}[req.status]
    push.notify(background, db, [req.tenancy.tenant_id], f"Your request is now {label}",
                req.landlord_note or req.title, "request")
    return request_out(req)
