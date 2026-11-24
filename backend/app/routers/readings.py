from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, HTTPException, Response, UploadFile
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, ConfigDict, Field
from fastapi.responses import FileResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..config import settings
from ..database import get_db
from ..deps import active_tenancy, current_user, landlord_only
from ..models import Bill, MeterReading, Tenancy, User, utcnow
from ..schemas import MyUsage, ReadingOut
from ..services import meter_reader, push
from ..services.tenancy import latest_value

router = APIRouter(tags=["readings"])


def reading_out(r: MeterReading) -> ReadingOut:
    used = round(r.value - r.previous_value, 3)
    return ReadingOut(
        id=r.id, tenancy_id=r.tenancy_id, value=r.value, previous_value=r.previous_value,
        units_used=used, cost=round(used * settings.water_rate_per_unit, 2),
        source=r.source, has_photo=bool(r.image_path), created_at=r.created_at,
    )


def _month_start() -> datetime:
    now = utcnow()
    return now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)


class ReadingScanOut(BaseModel):
    # The inference service returns a dataclass; allow response validation from its attributes.
    model_config = ConfigDict(from_attributes=True)

    value: float = Field(ge=0)
    confidence: float = Field(ge=0, le=1)
    per_digit_confidence: list[float]
    digits: str


@router.post("/readings/scan", response_model=ReadingScanOut)
async def scan_reading(photo: UploadFile = File(...), _: User = Depends(landlord_only)):
    """Suggest a meter reading; a landlord must still review and save it manually."""
    if not (photo.content_type or "").startswith("image/"):
        raise HTTPException(422, "Please upload a meter photo.")
    data = await photo.read(settings.max_photo_mb * 1024 * 1024 + 1)
    if not data:
        raise HTTPException(422, "The uploaded photo is empty.")
    if len(data) > settings.max_photo_mb * 1024 * 1024:
        raise HTTPException(413, f"The photo is larger than {settings.max_photo_mb} MB.")
    try:
        suggestion = await run_in_threadpool(meter_reader.read_meter, data)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    if suggestion is None:
        raise HTTPException(
            503,
            "Meter AI is unavailable. Install the backend inference requirements and copy the three .keras "
            "files plus meter_reader_config.json into backend/models/meter_reader, then retry.",
        )
    return suggestion


@router.post("/readings", response_model=ReadingOut, status_code=201)
async def save_reading(
    background: BackgroundTasks,
    tenancy_id: int = Form(...),
    value: float = Form(..., ge=0),
    confirm_large_jump: bool = Form(False),
    source: str = Form("MANUAL"),
    recorded_at: datetime | None = Form(None),  # set by the app when a reading was queued offline
    photo: UploadFile | None = File(None),
    db: Session = Depends(get_db),
    landlord: User = Depends(landlord_only),
):
    tenancy = db.get(Tenancy, tenancy_id)
    if tenancy is None or not tenancy.active:
        raise HTTPException(404, "Active tenant not found.")
    if source not in ("MANUAL", "AI"):
        raise HTTPException(422, "Unknown reading source.")

    previous = latest_value(db, tenancy)
    if value < previous:
        raise HTTPException(
            422, f"This is lower than the last reading ({previous:g}). Check the meter and try again."
        )
    used = value - previous
    if used > settings.large_jump_units and not confirm_large_jump:
        raise HTTPException(
            409, f"That is {used:g} units since the last reading ({previous:g}). Is this correct?"
        )

    image_path = None
    if photo is not None and photo.filename:
        if not (photo.content_type or "").startswith("image/"):
            raise HTTPException(422, "The meter photo must be an image.")
        data = await photo.read()
        if len(data) > settings.max_photo_mb * 1024 * 1024:
            raise HTTPException(413, f"The photo is larger than {settings.max_photo_mb} MB.")
        folder = Path(settings.upload_dir) / "readings"
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / f"{uuid4().hex}.jpg"
        path.write_bytes(data)
        image_path = str(path)

    taken_at = utcnow()
    if recorded_at is not None:
        if recorded_at.tzinfo is not None:
            recorded_at = recorded_at.astimezone(timezone.utc).replace(tzinfo=None)
        taken_at = min(recorded_at, taken_at)  # never in the future

    last_at = db.scalar(select(MeterReading.created_at).where(MeterReading.tenancy_id == tenancy.id)
                        .order_by(MeterReading.id.desc()).limit(1))
    if last_at is not None and taken_at < last_at:
        raise HTTPException(422, f"That date is before the last reading ({last_at:%d %b %Y}). Pick a later date.")

    reading = MeterReading(tenancy_id=tenancy.id, value=value, previous_value=previous,
                           image_path=image_path, source=source, recorded_by=landlord.id, created_at=taken_at)
    db.add(reading)
    db.flush()
    out = reading_out(reading)
    bill_note = ""
    if out.cost > 0:   # the water bill appears on the tenant's dashboard straight away
        db.add(Bill(tenancy_id=tenancy.id, kind="WATER", description=f"Water, {out.units_used:g} units",
                    amount=out.cost, due_date=date.today() + timedelta(days=settings.bill_due_days),
                    reading_id=reading.id))
        bill_note = f" Bill: KES {out.cost:,.0f}."
    db.commit()
    push.notify(background, db, [tenancy.tenant_id], "Water reading recorded",
                f"{out.units_used:g} units used since the last reading.{bill_note}", "reading")
    return out


@router.get("/tenants/{tenancy_id}/readings", response_model=list[ReadingOut])
def tenant_readings(tenancy_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)):
    tenancy = db.get(Tenancy, tenancy_id)
    if tenancy is None:
        raise HTTPException(404, "Tenant not found.")
    if user.role != "LANDLORD" and tenancy.tenant_id != user.id:
        raise HTTPException(403, "You can only view your own readings.")
    rows = db.scalars(
        select(MeterReading).where(MeterReading.tenancy_id == tenancy_id).order_by(MeterReading.id.desc())
    ).all()
    return [reading_out(r) for r in rows]


@router.get("/readings/mine", response_model=MyUsage)
def my_usage(db: Session = Depends(get_db), user: User = Depends(current_user)):
    tenancy = active_tenancy(db, user)
    if tenancy is None:
        raise HTTPException(404, "No active tenancy found.")
    rows = db.scalars(
        select(MeterReading).where(MeterReading.tenancy_id == tenancy.id).order_by(MeterReading.id.desc())
    ).all()
    out = [reading_out(r) for r in rows]
    start = _month_start()
    month_units = round(sum(o.units_used for o, r in zip(out, rows) if r.created_at >= start), 3)
    return MyUsage(
        unit_number=tenancy.unit.unit_number, rate_per_unit=settings.water_rate_per_unit,
        latest_reading=latest_value(db, tenancy), month_units=month_units,
        month_cost=round(month_units * settings.water_rate_per_unit, 2), readings=out,
    )


def _can_view(db: Session, user: User, reading: MeterReading) -> bool:
    if user.role == "LANDLORD":
        return True
    tenancy = db.get(Tenancy, reading.tenancy_id)
    return tenancy is not None and tenancy.tenant_id == user.id


@router.get("/readings/{reading_id}/photo")
def reading_photo(reading_id: int, db: Session = Depends(get_db), user: User = Depends(current_user)):
    reading = db.get(MeterReading, reading_id)
    if reading is None or not reading.image_path:
        raise HTTPException(404, "No photo for this reading.")
    if not _can_view(db, user, reading):
        raise HTTPException(403, "You can only view your own meter photos.")
    path = Path(reading.image_path)
    if not path.is_file():
        raise HTTPException(404, "The photo file is missing.")
    return FileResponse(path, media_type="image/jpeg")


@router.delete("/readings/{reading_id}", status_code=204)
def delete_reading(reading_id: int, db: Session = Depends(get_db), _: User = Depends(landlord_only)):
    """Undo a mistaken reading. Only the latest one per tenant, so billing history stays consistent."""
    reading = db.get(MeterReading, reading_id)
    if reading is None:
        raise HTTPException(404, "Reading not found.")
    latest_id = db.scalar(select(func.max(MeterReading.id)).where(MeterReading.tenancy_id == reading.tenancy_id))
    if reading.id != latest_id:
        raise HTTPException(409, "Only the latest reading can be deleted.")
    bill = db.scalar(select(Bill).where(Bill.reading_id == reading.id))
    if bill is not None:
        if bill.status == "PAID":
            raise HTTPException(409, "The water bill for this reading is already paid, so it can't be deleted.")
        db.delete(bill)
    if reading.image_path:
        Path(reading.image_path).unlink(missing_ok=True)
    db.delete(reading)
    db.commit()
    return Response(status_code=204)
