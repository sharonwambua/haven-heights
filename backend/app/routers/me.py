from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import active_tenancy, current_user, landlord_only
from ..models import User
from ..schemas import EstateOut, EstateUpdate, MeOut, ProfileUpdate
from ..services.tenancy import estate_out, get_estate, tenant_out

router = APIRouter(tags=["profile"])


def _me(db: Session, user: User) -> MeOut:
    tenancy = active_tenancy(db, user) if user.role == "TENANT" else None
    return MeOut(role=user.role, name=user.name, phone=user.phone, email=user.email,
                 emergency_contact=user.emergency_contact,
                 tenant=tenant_out(db, tenancy) if tenancy else None, estate=estate_out(db))


@router.get("/me", response_model=MeOut)
def me(db: Session = Depends(get_db), user: User = Depends(current_user)):
    return _me(db, user)


@router.patch("/me", response_model=MeOut)
def update_me(body: ProfileUpdate, db: Session = Depends(get_db), user: User = Depends(current_user)):
    sent = body.model_fields_set
    if "name" in sent and body.name:
        user.name = body.name.strip()
    if "email" in sent:
        email = (body.email or "").strip()
        if email and ("@" not in email or "." not in email.split("@")[-1]):
            raise HTTPException(422, "Enter a valid email address, or leave it empty.")
        user.email = email or None
    if "emergency_contact" in sent:
        user.emergency_contact = (body.emergency_contact or "").strip() or None
    db.commit()
    return _me(db, user)


@router.get("/estate", response_model=EstateOut)
def get_estate_info(db: Session = Depends(get_db), _: User = Depends(current_user)):
    return estate_out(db)


@router.patch("/estate", response_model=EstateOut)
def update_estate(body: EstateUpdate, db: Session = Depends(get_db), _: User = Depends(landlord_only)):
    row = get_estate(db)
    row.estate_name = body.estate_name.strip()
    row.address = body.address.strip()
    row.payment_info = body.payment_info.strip()
    db.commit()
    return estate_out(db)
