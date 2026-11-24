from datetime import date, datetime, timezone

from sqlalchemy import Boolean, Date, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)  # stored as naive UTC


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    role: Mapped[str] = mapped_column(String(10))  # LANDLORD | TENANT
    name: Mapped[str] = mapped_column(String(80))
    phone: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(100))
    email: Mapped[str | None] = mapped_column(String(120), nullable=True)
    emergency_contact: Mapped[str | None] = mapped_column(String(120), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Unit(Base):
    __tablename__ = "units"
    id: Mapped[int] = mapped_column(primary_key=True)
    unit_number: Mapped[str] = mapped_column(String(12), unique=True)
    unit_type: Mapped[str] = mapped_column(String(30), default="Bedsitter")
    status: Mapped[str] = mapped_column(String(10), default="VACANT")  # VACANT | OCCUPIED


class Tenancy(Base):
    __tablename__ = "tenancies"
    id: Mapped[int] = mapped_column(primary_key=True)
    tenant_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"))
    move_in_date: Mapped[date] = mapped_column(Date)
    initial_meter_reading: Mapped[float] = mapped_column(Float, default=0)
    active: Mapped[bool] = mapped_column(Boolean, default=True)
    # lease
    monthly_rent: Mapped[float] = mapped_column(Float, default=0, server_default="0")
    deposit: Mapped[float] = mapped_column(Float, default=0, server_default="0")
    rent_due_day: Mapped[int] = mapped_column(Integer, default=5, server_default="5")
    lease_end: Mapped[date | None] = mapped_column(Date, nullable=True)
    lease_terms: Mapped[str | None] = mapped_column(Text, nullable=True)
    lease_file_path: Mapped[str | None] = mapped_column(String(255), nullable=True)
    lease_file_name: Mapped[str | None] = mapped_column(String(120), nullable=True)

    tenant: Mapped[User] = relationship()
    unit: Mapped[Unit] = relationship()


class MeterReading(Base):
    __tablename__ = "meter_readings"
    id: Mapped[int] = mapped_column(primary_key=True)
    tenancy_id: Mapped[int] = mapped_column(ForeignKey("tenancies.id"), index=True)
    value: Mapped[float] = mapped_column(Float)
    previous_value: Mapped[float] = mapped_column(Float)
    image_path: Mapped[str | None] = mapped_column(String(255), nullable=True)
    source: Mapped[str] = mapped_column(String(10), default="MANUAL")  # MANUAL | AI
    confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    recorded_by: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class MaintenanceRequest(Base):
    __tablename__ = "maintenance_requests"
    id: Mapped[int] = mapped_column(primary_key=True)
    tenancy_id: Mapped[int] = mapped_column(ForeignKey("tenancies.id"), index=True)
    title: Mapped[str] = mapped_column(String(120))
    description: Mapped[str] = mapped_column(Text)
    urgency: Mapped[str] = mapped_column(String(10))   # LOW | MEDIUM | HIGH
    status: Mapped[str] = mapped_column(String(12), default="OPEN")  # OPEN | IN_PROGRESS | RESOLVED
    landlord_note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)

    tenancy: Mapped[Tenancy] = relationship()


class Announcement(Base):
    __tablename__ = "announcements"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(120))
    body: Mapped[str] = mapped_column(Text)
    category: Mapped[str] = mapped_column(String(20), default="GENERAL")
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class DeviceToken(Base):
    __tablename__ = "device_tokens"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    token: Mapped[str] = mapped_column(String(512), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class EstateSettings(Base):
    """One row: how the estate presents itself and how tenants should pay."""
    __tablename__ = "estate_settings"
    id: Mapped[int] = mapped_column(primary_key=True)
    estate_name: Mapped[str] = mapped_column(String(80), default="Haven Heights")
    address: Mapped[str] = mapped_column(String(200), default="")
    payment_info: Mapped[str] = mapped_column(Text, default="")


class Bill(Base):
    __tablename__ = "bills"
    id: Mapped[int] = mapped_column(primary_key=True)
    tenancy_id: Mapped[int] = mapped_column(ForeignKey("tenancies.id"), index=True)
    kind: Mapped[str] = mapped_column(String(10))            # RENT | WATER | OTHER
    description: Mapped[str] = mapped_column(String(160))
    amount: Mapped[float] = mapped_column(Float)
    due_date: Mapped[date] = mapped_column(Date)
    status: Mapped[str] = mapped_column(String(10), default="PENDING")  # PENDING | SUBMITTED | PAID
    paid_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    reading_id: Mapped[int | None] = mapped_column(ForeignKey("meter_readings.id"), nullable=True)
    period: Mapped[str | None] = mapped_column(String(7), nullable=True)  # YYYY-MM, for monthly rent
    payment_reference: Mapped[str | None] = mapped_column(String(60), nullable=True)  # M-Pesa code from the tenant
    submitted_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    review_note: Mapped[str | None] = mapped_column(String(300), nullable=True)      # why a payment was not accepted
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    tenancy: Mapped[Tenancy] = relationship()


class Notification(Base):
    """In-app inbox. Every push is also stored here so it can be read later."""
    __tablename__ = "notifications"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    title: Mapped[str] = mapped_column(String(120))
    body: Mapped[str] = mapped_column(Text)
    kind: Mapped[str] = mapped_column(String(12))
    read: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
