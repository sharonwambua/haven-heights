from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from ..config import settings
from ..database import get_db
from ..deps import current_user, landlord_only
from ..models import Tenancy, User
from ..schemas import LeaseUpdate, TenantOut
from ..services.tenancy import tenant_out

router = APIRouter(prefix="/leases", tags=["leases"])

_TYPES = {"application/pdf": ".pdf", "image/jpeg": ".jpg", "image/png": ".png"}


@router.patch("/{tenancy_id}", response_model=TenantOut)
def update_lease(tenancy_id: int, body: LeaseUpdate, db: Session = Depends(get_db), _: User = Depends(landlord_only)):
    t = db.get(Tenancy, tenancy_id)
    if t is None or not t.active:
        raise HTTPException(404, "Active tenant not found.")
    if body.lease_end and body.lease_end <= t.move_in_date:
        raise HTTPException(422, "The lease end date must be after the move-in date.")
    t.monthly_rent, t.deposit, t.rent_due_day = body.monthly_rent, body.deposit, body.rent_due_day
    t.lease_end = body.lease_end
    t.lease_terms = (body.lease_terms or "").strip() or None
    db.commit()
    return tenant_out(db, t)


@router.post("/{tenancy_id}/file", response_model=TenantOut)
async def upload_agreement(tenancy_id: int, file: UploadFile = File(...), db: Session = Depends(get_db),
                           _: User = Depends(landlord_only)):
    t = db.get(Tenancy, tenancy_id)
    if t is None or not t.active:
        raise HTTPException(404, "Active tenant not found.")
    ext = _TYPES.get(file.content_type or "")
    if ext is None:
        raise HTTPException(422, "Attach the agreement as a PDF, JPG or PNG.")
    data = await file.read()
    if len(data) > settings.max_photo_mb * 1024 * 1024:
        raise HTTPException(413, f"The file is larger than {settings.max_photo_mb} MB.")
    folder = Path(settings.upload_dir) / "leases"
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{uuid4().hex}{ext}"
    path.write_bytes(data)
    if t.lease_file_path:
        Path(t.lease_file_path).unlink(missing_ok=True)
    t.lease_file_path = str(path)
    t.lease_file_name = (Path(file.filename or "").name or f"lease{ext}")[:120]
    db.commit()
    return tenant_out(db, t)


@router.get("/{tenancy_id}/file")
def download_agreement(tenancy_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)):
    t = db.get(Tenancy, tenancy_id)
    if t is None or not t.lease_file_path:
        raise HTTPException(404, "No lease agreement has been attached yet.")
    if user.role != "LANDLORD" and t.tenant_id != user.id:
        raise HTTPException(403, "You can only view your own lease agreement.")
    path = Path(t.lease_file_path)
    if not path.is_file():
        raise HTTPException(404, "The agreement file is missing.")
    media = next((m for m, e in _TYPES.items() if path.suffix == e), "application/octet-stream")
    return FileResponse(path, media_type=media, filename=t.lease_file_name or path.name)
