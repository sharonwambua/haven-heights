from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from .database import get_db
from .models import Tenancy, User
from .security import decode_token

bearer = HTTPBearer(auto_error=False)


def current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User:
    if creds is None:
        raise HTTPException(401, "Sign in to continue.")
    user_id = decode_token(creds.credentials)
    user = db.get(User, user_id) if user_id else None
    if user is None:
        raise HTTPException(401, "Your session has expired. Sign in again.")
    return user


def landlord_only(user: User = Depends(current_user)) -> User:
    if user.role != "LANDLORD":
        raise HTTPException(403, "Only the landlord can do this.")
    return user


def active_tenancy(db: Session, user: User) -> Tenancy | None:
    return db.scalar(
        select(Tenancy).where(Tenancy.tenant_id == user.id, Tenancy.active.is_(True))
    )
