from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import MeterReading, Tenancy
from ..models import Bill, EstateSettings, User
from ..schemas import BillOut, EstateOut, TenantOut


def latest_value(db: Session, tenancy: Tenancy) -> float:
    value = db.scalar(
        select(MeterReading.value)
        .where(MeterReading.tenancy_id == tenancy.id)
        .order_by(MeterReading.id.desc())
        .limit(1)
    )
    return tenancy.initial_meter_reading if value is None else value


def tenant_out(db: Session, t: Tenancy) -> TenantOut:
    return TenantOut(
        tenancy_id=t.id,
        name=t.tenant.name,
        phone=t.tenant.phone,
        unit_number=t.unit.unit_number,
        unit_type=t.unit.unit_type,
        move_in_date=t.move_in_date,
        initial_meter_reading=t.initial_meter_reading,
        latest_reading=latest_value(db, t),
        active=t.active,
        email=t.tenant.email,
        emergency_contact=t.tenant.emergency_contact,
        monthly_rent=t.monthly_rent,
        deposit=t.deposit,
        rent_due_day=t.rent_due_day,
        lease_end=t.lease_end,
        lease_terms=t.lease_terms,
        has_lease_file=bool(t.lease_file_path),
        lease_file_name=t.lease_file_name,
    )


def get_estate(db: Session) -> EstateSettings:
    row = db.get(EstateSettings, 1)
    if row is None:
        row = EstateSettings(id=1)
        db.add(row)
        db.commit()
    return row


def estate_out(db: Session) -> EstateOut:
    row = get_estate(db)
    landlord = db.scalar(select(User).where(User.role == "LANDLORD"))
    return EstateOut(estate_name=row.estate_name, address=row.address, payment_info=row.payment_info,
                     landlord_name=landlord.name if landlord else "", landlord_phone=landlord.phone if landlord else "")


def bill_out(b: Bill) -> BillOut:
    from datetime import date
    return BillOut(
        id=b.id, tenancy_id=b.tenancy_id, tenant_name=b.tenancy.tenant.name, unit_number=b.tenancy.unit.unit_number,
        kind=b.kind, description=b.description, amount=b.amount, due_date=b.due_date, status=b.status,
        overdue=b.status == "PENDING" and b.due_date < date.today(), paid_at=b.paid_at, created_at=b.created_at,
        payment_reference=b.payment_reference, submitted_at=b.submitted_at, review_note=b.review_note,
    )
