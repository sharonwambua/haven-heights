from datetime import datetime

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import landlord_only
from datetime import date

from ..models import Bill, MaintenanceRequest, MeterReading, Tenancy, Unit, utcnow
from ..schemas import Overview

router = APIRouter(prefix="/overview", tags=["overview"], dependencies=[Depends(landlord_only)])


@router.get("", response_model=Overview)
def overview(db: Session = Depends(get_db)):
    month_start = utcnow().replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    open_q = MaintenanceRequest.status != "RESOLVED"
    return Overview(
        tenants=db.scalar(select(func.count()).select_from(Tenancy).where(Tenancy.active.is_(True))) or 0,
        vacant_units=db.scalar(select(func.count()).select_from(Unit).where(Unit.status == "VACANT")) or 0,
        open_requests=db.scalar(select(func.count()).select_from(MaintenanceRequest).where(open_q)) or 0,
        high_urgency_open=db.scalar(
            select(func.count()).select_from(MaintenanceRequest)
            .where(open_q, MaintenanceRequest.urgency == "HIGH")) or 0,
        readings_this_month=db.scalar(
            select(func.count()).select_from(MeterReading).where(MeterReading.created_at >= month_start)) or 0,
        pending_bills_total=db.scalar(select(func.coalesce(func.sum(Bill.amount), 0.0)).where(Bill.status != "PAID")) or 0,
        awaiting_approval=db.scalar(select(func.count()).select_from(Bill).where(Bill.status == "SUBMITTED")) or 0,
        overdue_bills=db.scalar(select(func.count()).select_from(Bill)
                                .where(Bill.status == "PENDING", Bill.due_date < date.today())) or 0,
    )
