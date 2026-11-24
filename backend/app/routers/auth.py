from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..database import get_db
from ..deps import active_tenancy, current_user
from ..models import User
from ..schemas import ChangePasswordIn, LoginIn, TokenOut
from ..security import create_token, hash_password, normalize_phone, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=TokenOut)
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.phone == normalize_phone(body.phone)))
    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "Phone number or password is wrong.")
    tenancy_id = None
    if user.role == "TENANT":
        tenancy = active_tenancy(db, user)
        if tenancy is None:
            raise HTTPException(403, "This tenancy has ended. Contact your landlord.")
        tenancy_id = tenancy.id
    return TokenOut(
        access_token=create_token(user.id), role=user.role, name=user.name, tenancy_id=tenancy_id
    )


@router.post("/change-password", status_code=204)
def change_password(body: ChangePasswordIn, db: Session = Depends(get_db), user: User = Depends(current_user)):
    if not verify_password(body.current_password, user.password_hash):
        raise HTTPException(422, "Your current password is wrong.")
    user.password_hash = hash_password(body.new_password)
    db.commit()
    return Response(status_code=204)
