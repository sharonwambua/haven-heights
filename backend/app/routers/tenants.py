import secrets

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import landlord_only
from ..models import Tenancy, Unit, User
from ..schemas import TenantCreate, TenantCreated, TenantOut
from ..security import hash_password, normalize_phone
from ..services.tenancy import tenant_out

router = APIRouter(prefix="/tenants", tags=["tenants"], dependencies=[Depends(landlord_only)])


@router.post("", response_model=TenantCreated, status_code=201)
def register_tenant(body: TenantCreate, db: Session = Depends(get_db)):
    phone = normalize_phone(body.phone)
    if len(phone) < 10:
        raise HTTPException(422, "Enter a valid phone number.")
    if db.scalar(select(User).where(User.phone == phone)):
        raise HTTPException(409, "A user with this phone number already exists.")

    number = body.unit_number.strip().upper()
    unit = db.scalar(select(Unit).where(Unit.unit_number == number))
    if unit is None:
        unit = Unit(unit_number=number, unit_type=body.unit_type)
        db.add(unit)
        db.flush()
    elif unit.status == "OCCUPIED":
        raise HTTPException(409, f"Unit {number} already has a tenant.")
    unit.unit_type = body.unit_type
    unit.status = "OCCUPIED"

    temp_password = body.password or secrets.token_urlsafe(6)
    user = User(role="TENANT", name=body.name.strip(), phone=phone,
                password_hash=hash_password(temp_password))
    db.add(user)
    db.flush()
    tenancy = Tenancy(tenant_id=user.id, unit_id=unit.id, move_in_date=body.move_in_date,
                      initial_meter_reading=body.initial_meter_reading, monthly_rent=body.monthly_rent,
                      deposit=body.deposit, rent_due_day=body.rent_due_day)
    db.add(tenancy)
    db.commit()
    return TenantCreated(tenant=tenant_out(db, tenancy), temporary_password=temp_password)


@router.get("", response_model=list[TenantOut])
def list_tenants(active: bool = True, db: Session = Depends(get_db)):
    rows = db.scalars(select(Tenancy).where(Tenancy.active.is_(active)).order_by(Tenancy.id)).all()
    return sorted((tenant_out(db, t) for t in rows), key=lambda t: t.unit_number)


@router.get("/{tenancy_id}", response_model=TenantOut)
def get_tenant(tenancy_id: int, db: Session = Depends(get_db)):
    tenancy = db.get(Tenancy, tenancy_id)
    if tenancy is None:
        raise HTTPException(404, "Tenant not found.")
    return tenant_out(db, tenancy)


@router.patch("/{tenancy_id}/move-out", response_model=TenantOut)
def move_out(tenancy_id: int, db: Session = Depends(get_db)):
    tenancy = db.get(Tenancy, tenancy_id)
    if tenancy is None or not tenancy.active:
        raise HTTPException(404, "Active tenancy not found.")
    tenancy.active = False
    tenancy.unit.status = "VACANT"
    db.commit()
    return tenant_out(db, tenancy)
