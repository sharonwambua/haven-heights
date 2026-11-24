from datetime import date, datetime, timezone

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Response
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import active_tenancy, current_user, landlord_only
from ..models import Bill, Tenancy, User
from ..schemas import BillCreate, BillOut, BillStatus, BillStatusUpdate, RentRun, ReviewIn, SubmitPaymentIn
from ..services import push
from ..services.tenancy import bill_out

router = APIRouter(prefix="/bills", tags=["bills"])


def _money(v: float) -> str:
    return f"KES {v:,.0f}"


@router.get("", response_model=list[BillOut])
def list_bills(status: BillStatus | None = None, tenancy_id: int | None = None,
               db: Session = Depends(get_db), user: User = Depends(current_user)):
    query = select(Bill).order_by(Bill.status.desc(), Bill.due_date, Bill.id.desc())  # PENDING sorts before PAID
    if user.role == "LANDLORD":
        if tenancy_id:
            query = query.where(Bill.tenancy_id == tenancy_id)
    else:
        tenancy = active_tenancy(db, user)
        if tenancy is None:
            return []
        query = query.where(Bill.tenancy_id == tenancy.id)
    if status:
        query = query.where(Bill.status == status.value)
    return [bill_out(b) for b in db.scalars(query).all()]


@router.post("", response_model=BillOut, status_code=201)
def create_bill(body: BillCreate, background: BackgroundTasks, db: Session = Depends(get_db),
                _: User = Depends(landlord_only)):
    tenancy = db.get(Tenancy, body.tenancy_id)
    if tenancy is None or not tenancy.active:
        raise HTTPException(404, "Active tenant not found.")
    bill = Bill(tenancy_id=tenancy.id, kind=body.kind.value, description=body.description.strip(),
                amount=body.amount, due_date=body.due_date)
    db.add(bill)
    db.commit()
    push.notify(background, db, [tenancy.tenant_id], f"New bill: {_money(bill.amount)}",
                f"{bill.description}, due {bill.due_date:%d %b %Y}", "bill")
    return bill_out(bill)


@router.post("/generate-rent", response_model=RentRun)
def generate_rent(background: BackgroundTasks, db: Session = Depends(get_db), _: User = Depends(landlord_only)):
    """Create this month's rent bill for every tenant with rent set. Safe to press twice."""
    today = date.today()
    period = today.strftime("%Y-%m")
    created = 0
    for t in db.scalars(select(Tenancy).where(Tenancy.active.is_(True), Tenancy.monthly_rent > 0)):
        exists = db.scalar(select(Bill.id).where(Bill.tenancy_id == t.id, Bill.kind == "RENT", Bill.period == period))
        if exists:
            continue
        bill = Bill(tenancy_id=t.id, kind="RENT", description=f"Rent, {today:%B %Y}", amount=t.monthly_rent,
                    due_date=date(today.year, today.month, t.rent_due_day), period=period)
        db.add(bill)
        db.commit()
        push.notify(background, db, [t.tenant_id], f"Rent bill: {_money(bill.amount)}",
                    f"{bill.description}, due {bill.due_date:%d %b %Y}", "bill")
        created += 1
    return RentRun(created=created)


@router.patch("/{bill_id}/status", response_model=BillOut)
def set_status(bill_id: int, body: BillStatusUpdate, background: BackgroundTasks, db: Session = Depends(get_db),
               _: User = Depends(landlord_only)):
    bill = db.get(Bill, bill_id)
    if bill is None:
        raise HTTPException(404, "Bill not found.")
    if body.status == BillStatus.SUBMITTED:
        raise HTTPException(422, "Only the tenant can submit a payment.")
    was = bill.status
    bill.status = body.status.value
    bill.paid_at = datetime.now(timezone.utc).replace(tzinfo=None) if body.status == BillStatus.PAID else None
    if body.status == BillStatus.PENDING:   # marking unpaid wipes the old payment claim
        bill.payment_reference = bill.submitted_at = bill.review_note = None
    db.commit()
    if was != bill.status and bill.status == "PAID":
        push.notify(background, db, [bill.tenancy.tenant_id], "Payment received",
                    f"{bill.description}: {_money(bill.amount)} marked as paid. Thank you.", "bill")
    return bill_out(bill)


@router.delete("/{bill_id}", status_code=204)
def delete_bill(bill_id: int, db: Session = Depends(get_db), _: User = Depends(landlord_only)):
    bill = db.get(Bill, bill_id)
    if bill is None:
        raise HTTPException(404, "Bill not found.")
    if bill.status == "PAID":
        raise HTTPException(409, "A paid bill is a record of payment and can't be deleted.")
    if bill.status == "SUBMITTED":
        raise HTTPException(409, "The tenant has submitted a payment for this bill. Review it first.")
    db.delete(bill)
    db.commit()
    return Response(status_code=204)


@router.post("/{bill_id}/submit-payment", response_model=BillOut)
def submit_payment(bill_id: int, body: SubmitPaymentIn, background: BackgroundTasks, db: Session = Depends(get_db),
                   user: User = Depends(current_user)):
    """Tenant: "I have paid". The landlord then approves or rejects it."""
    bill = db.get(Bill, bill_id)
    if user.role != "TENANT" or bill is None or bill.tenancy.tenant_id != user.id:
        raise HTTPException(404, "Bill not found.")
    if bill.status == "PAID":
        raise HTTPException(409, "This bill is already paid.")
    if bill.status == "SUBMITTED":
        raise HTTPException(409, "You already submitted a payment. Your landlord will confirm it.")
    bill.status = "SUBMITTED"
    bill.payment_reference = body.reference.strip().upper()
    bill.submitted_at = datetime.now(timezone.utc).replace(tzinfo=None)
    bill.review_note = None
    db.commit()
    push.notify(background, db, push.landlord_user_ids(db), f"Payment to approve: {_money(bill.amount)}",
                f"Unit {bill.tenancy.unit.unit_number}, {user.name}: {bill.description}. Code {bill.payment_reference}.", "bill")
    return bill_out(bill)


@router.post("/{bill_id}/review", response_model=BillOut)
def review_payment(bill_id: int, body: ReviewIn, background: BackgroundTasks, db: Session = Depends(get_db),
                   _: User = Depends(landlord_only)):
    """Landlord: approve a submitted payment (it becomes PAID) or reject it (back to PENDING with a note)."""
    bill = db.get(Bill, bill_id)
    if bill is None:
        raise HTTPException(404, "Bill not found.")
    if bill.status != "SUBMITTED":
        raise HTTPException(409, "There is no submitted payment to review on this bill.")
    ref = bill.payment_reference or ""
    if body.approve:
        bill.status = "PAID"
        bill.paid_at = datetime.now(timezone.utc).replace(tzinfo=None)
        bill.review_note = None
        title, text = "Payment approved", f"{bill.description}: {_money(bill.amount)} is confirmed as paid. Thank you."
    else:
        bill.status = "PENDING"
        reason = (body.note or "").strip() or "We could not find this payment."
        bill.review_note = f"Payment {ref} was not confirmed. {reason}"[:300]
        bill.payment_reference = bill.submitted_at = None
        title, text = "Payment not confirmed", f"{bill.description}: {bill.review_note}"
    db.commit()
    push.notify(background, db, [bill.tenancy.tenant_id], title, text, "bill")
    return bill_out(bill)
