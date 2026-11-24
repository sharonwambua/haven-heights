from datetime import date, datetime, timezone
from enum import Enum
from typing import Annotated

from pydantic import BaseModel, Field, PlainSerializer


def _utc_iso(value: datetime) -> str:
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.isoformat()


UtcDatetime = Annotated[datetime, PlainSerializer(_utc_iso, return_type=str)]


class Urgency(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class RequestStatus(str, Enum):
    OPEN = "OPEN"
    IN_PROGRESS = "IN_PROGRESS"
    RESOLVED = "RESOLVED"


class Category(str, Enum):
    WATER = "WATER"
    MAINTENANCE = "MAINTENANCE"
    GARBAGE = "GARBAGE"
    GENERAL = "GENERAL"


# ---- auth ----
class LoginIn(BaseModel):
    phone: str
    password: str = Field(max_length=64)


class ChangePasswordIn(BaseModel):
    current_password: str = Field(max_length=64)
    new_password: str = Field(min_length=6, max_length=64)


class DeviceIn(BaseModel):
    token: str = Field(min_length=10, max_length=512)


class TokenOut(BaseModel):
    access_token: str
    role: str
    name: str
    tenancy_id: int | None = None


# ---- tenants ----
class TenantCreate(BaseModel):
    monthly_rent: float = Field(default=0, ge=0)
    deposit: float = Field(default=0, ge=0)
    rent_due_day: int = Field(default=5, ge=1, le=28)
    name: str = Field(min_length=2, max_length=80)
    phone: str = Field(min_length=9, max_length=20)
    unit_number: str = Field(min_length=1, max_length=12)
    unit_type: str = Field(default="Bedsitter", max_length=30)
    move_in_date: date
    initial_meter_reading: float = Field(ge=0)
    password: str | None = Field(default=None, min_length=6, max_length=64)


class TenantOut(BaseModel):
    tenancy_id: int
    name: str
    phone: str
    unit_number: str
    unit_type: str
    move_in_date: date
    initial_meter_reading: float
    latest_reading: float
    active: bool
    email: str | None = None
    emergency_contact: str | None = None
    monthly_rent: float = 0
    deposit: float = 0
    rent_due_day: int = 5
    lease_end: date | None = None
    lease_terms: str | None = None
    has_lease_file: bool = False
    lease_file_name: str | None = None


class LeaseUpdate(BaseModel):
    monthly_rent: float = Field(ge=0)
    deposit: float = Field(ge=0)
    rent_due_day: int = Field(ge=1, le=28)
    lease_end: date | None = None
    lease_terms: str | None = Field(default=None, max_length=4000)


class TenantCreated(BaseModel):
    tenant: TenantOut
    temporary_password: str


# ---- readings ----
class ReadingOut(BaseModel):
    id: int
    tenancy_id: int
    value: float
    previous_value: float
    units_used: float
    cost: float
    source: str
    has_photo: bool
    created_at: UtcDatetime


class MyUsage(BaseModel):
    unit_number: str
    rate_per_unit: float
    latest_reading: float
    month_units: float
    month_cost: float
    readings: list[ReadingOut]


# ---- maintenance ----
class RequestCreate(BaseModel):
    title: str = Field(min_length=3, max_length=120)
    description: str = Field(min_length=3, max_length=2000)
    urgency: Urgency = Urgency.MEDIUM
    tenancy_id: int | None = None  # landlord logging on behalf of a tenant


class StatusUpdate(BaseModel):
    status: RequestStatus
    landlord_note: str | None = Field(default=None, max_length=1000)


class RequestOut(BaseModel):
    id: int
    tenancy_id: int
    tenant_name: str
    unit_number: str
    title: str
    description: str
    urgency: Urgency
    status: RequestStatus
    landlord_note: str | None
    created_at: UtcDatetime
    updated_at: UtcDatetime


# ---- announcements ----
class AnnouncementCreate(BaseModel):
    title: str = Field(min_length=3, max_length=120)
    body: str = Field(min_length=3, max_length=4000)
    category: Category = Category.GENERAL


class AnnouncementOut(BaseModel):
    id: int
    title: str
    body: str
    category: Category
    created_at: UtcDatetime


# ---- landlord dashboard ----
class Overview(BaseModel):
    tenants: int
    vacant_units: int
    open_requests: int
    high_urgency_open: int
    readings_this_month: int
    pending_bills_total: float = 0
    overdue_bills: int = 0
    awaiting_approval: int = 0


# ---- profile & estate ----
class EstateOut(BaseModel):
    estate_name: str
    address: str
    payment_info: str
    landlord_name: str
    landlord_phone: str


class EstateUpdate(BaseModel):
    estate_name: str = Field(min_length=2, max_length=80)
    address: str = Field(default="", max_length=200)
    payment_info: str = Field(default="", max_length=1000)


class ProfileUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=80)
    email: str | None = Field(default=None, max_length=120)
    emergency_contact: str | None = Field(default=None, max_length=120)


class MeOut(BaseModel):
    role: str
    name: str
    phone: str
    email: str | None
    emergency_contact: str | None
    tenant: TenantOut | None
    estate: EstateOut


# ---- bills ----
class BillKind(str, Enum):
    RENT = "RENT"
    WATER = "WATER"
    OTHER = "OTHER"


class BillStatus(str, Enum):
    PENDING = "PENDING"      # tenant still has to pay
    SUBMITTED = "SUBMITTED"  # tenant says they paid; waiting for the landlord to approve
    PAID = "PAID"            # landlord approved


class BillCreate(BaseModel):
    tenancy_id: int
    kind: BillKind = BillKind.OTHER
    description: str = Field(min_length=3, max_length=160)
    amount: float = Field(gt=0)
    due_date: date


class BillStatusUpdate(BaseModel):
    status: BillStatus


class SubmitPaymentIn(BaseModel):
    reference: str = Field(min_length=4, max_length=60)   # e.g. the M-Pesa confirmation code


class ReviewIn(BaseModel):
    approve: bool
    note: str | None = Field(default=None, max_length=300)


class BillOut(BaseModel):
    id: int
    tenancy_id: int
    tenant_name: str
    unit_number: str
    kind: BillKind
    description: str
    amount: float
    due_date: date
    status: BillStatus
    overdue: bool
    paid_at: UtcDatetime | None
    created_at: UtcDatetime
    payment_reference: str | None = None
    submitted_at: UtcDatetime | None = None
    review_note: str | None = None


class RentRun(BaseModel):
    created: int


# ---- notifications inbox ----
class NotificationOut(BaseModel):
    id: int
    title: str
    body: str
    kind: str
    read: bool
    created_at: UtcDatetime


class NotificationsOut(BaseModel):
    unread: int
    items: list[NotificationOut]


# ---- tenant dashboard ----
class DashboardOut(BaseModel):
    tenant: TenantOut
    estate: EstateOut
    pending_total: float
    awaiting_total: float = 0
    overdue_total: float
    pending_bills: list[BillOut]
    latest_notices: list[AnnouncementOut]
    open_requests: int
    unread_notifications: int
    water_month_units: float
    water_month_cost: float
