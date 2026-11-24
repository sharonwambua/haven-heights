from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import active_tenancy, current_user
from ..models import Announcement, Bill, MaintenanceRequest, MeterReading, Notification, User, utcnow
from ..schemas import AnnouncementOut, DashboardOut
from ..config import settings
from ..services.tenancy import bill_out, estate_out, tenant_out

router = APIRouter(tags=["dashboard"])


@router.get("/dashboard", response_model=DashboardOut)
def tenant_dashboard(db: Session = Depends(get_db), user: User = Depends(current_user)):
    if user.role != "TENANT":
        raise HTTPException(403, "The dashboard is for tenants.")
    tenancy = active_tenancy(db, user)
    if tenancy is None:
        raise HTTPException(404, "No active tenancy found.")

    pending = db.scalars(select(Bill).where(Bill.tenancy_id == tenancy.id, Bill.status != "PAID")
                         .order_by(Bill.due_date)).all()
    outs = [bill_out(b) for b in pending]
    notices = db.scalars(select(Announcement).order_by(Announcement.id.desc()).limit(3)).all()
    open_requests = db.scalar(select(func.count()).select_from(MaintenanceRequest)
                              .where(MaintenanceRequest.tenancy_id == tenancy.id,
                                     MaintenanceRequest.status != "RESOLVED")) or 0
    unread = db.scalar(select(func.count()).select_from(Notification)
                       .where(Notification.user_id == user.id, Notification.read.is_(False))) or 0
    month_start = utcnow().replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    month_units = sum(r.value - r.previous_value for r in db.scalars(
        select(MeterReading).where(MeterReading.tenancy_id == tenancy.id, MeterReading.created_at >= month_start)))

    return DashboardOut(
        tenant=tenant_out(db, tenancy), estate=estate_out(db),
        pending_total=sum(b.amount for b in outs if b.status == "PENDING"),
        awaiting_total=sum(b.amount for b in outs if b.status == "SUBMITTED"), overdue_total=sum(b.amount for b in outs if b.overdue),
        pending_bills=outs[:5],
        latest_notices=[AnnouncementOut(id=a.id, title=a.title, body=a.body, category=a.category,
                                        created_at=a.created_at) for a in notices],
        open_requests=open_requests, unread_notifications=unread,
        water_month_units=round(month_units, 3), water_month_cost=round(month_units * settings.water_rate_per_unit, 2),
    )
