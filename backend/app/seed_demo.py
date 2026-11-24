"""Idempotent starter data for a fresh Haven Heights installation.
Safe to run automatically at first startup or manually with ``python -m app.seed_demo``."""
from datetime import date

from sqlalchemy import select

from . import migrate
from .database import SessionLocal
from .main import ensure_landlord
from datetime import datetime, timedelta

from .models import Announcement, Bill, EstateSettings, MaintenanceRequest, MeterReading, Tenancy, Unit, User
from .security import hash_password

DEMO = [("Mary Mutua", "0712000001", "A1", 112.0), ("John Kioko", "0712000002", "A2", 98.5),
        ("Grace Nduku", "0712000003", "B1", 143.2)]


def seed_demo() -> None:
    migrate.upgrade()
    ensure_landlord()
    with SessionLocal() as db:
        landlord = db.scalar(select(User).where(User.role == "LANDLORD"))
        for name, phone, unit_no, start in DEMO:
            if db.scalar(select(User).where(User.phone == phone)):
                continue
            unit = Unit(unit_number=unit_no, unit_type="Bedsitter", status="OCCUPIED")
            user = User(role="TENANT", name=name, phone=phone, password_hash=hash_password("tenant123"))
            db.add_all([unit, user]); db.flush()
            t = Tenancy(tenant_id=user.id, unit_id=unit.id, move_in_date=date(2026, 9, 1), initial_meter_reading=start,
                        monthly_rent=6500, deposit=6500, rent_due_day=5, lease_end=date(2027, 8, 31),
                        lease_terms="12 months. One month written notice. No subletting.")
            db.add(t); db.flush()
            reading = MeterReading(tenancy_id=t.id, value=start + 8, previous_value=start, recorded_by=landlord.id)
            db.add(reading); db.flush()
            db.add(Bill(tenancy_id=t.id, kind="WATER", description="Water, 8 units", amount=1200,
                        due_date=date.today() + timedelta(days=5), reading_id=reading.id))
            db.add(Bill(tenancy_id=t.id, kind="RENT", description="Rent, last month", amount=6500,
                        due_date=date.today() - timedelta(days=4) if unit_no == "A2" else date.today() + timedelta(days=20),
                        # Mary (A1) has already paid by M-Pesa and is waiting for the landlord to approve it
                        status="SUBMITTED" if unit_no == "A1" else "PENDING",
                        payment_reference="SGH7K2LMQ9" if unit_no == "A1" else None,
                        submitted_at=datetime.utcnow() if unit_no == "A1" else None))
            if unit_no == "A2":
                db.add(MaintenanceRequest(tenancy_id=t.id, title="Bathroom tap leaking",
                                          description="Drips all night and the floor stays wet.", urgency="HIGH"))
        estate = db.get(EstateSettings, 1) or EstateSettings(id=1)
        estate.address = "Machakos town"
        estate.payment_info = "M-Pesa Paybill 123456, account = your unit number"
        db.add(estate)
        if not db.scalar(select(Announcement)):
            db.add(Announcement(title="Water off on Friday", body="No water from 8am to 2pm while the main pipe is repaired. Please store enough the night before.",
                                category="WATER", author_id=landlord.id))
        db.commit()
    print("Haven Heights starter data is ready. Landlord: 0700000000 / changeme123")


def main() -> None:
    seed_demo()


if __name__ == "__main__":
    main()
