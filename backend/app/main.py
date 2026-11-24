from contextlib import asynccontextmanager

from fastapi import FastAPI
from sqlalchemy import select

from .config import settings
from . import migrate
from .database import SessionLocal
from .models import User
from .routers import (announcements, auth, bills, dashboard, devices, leases, me, notifications, overview,
                      readings, requests, tenants)
from .security import hash_password, normalize_phone


def ensure_landlord() -> None:
    with SessionLocal() as db:
        if db.scalar(select(User).where(User.role == "LANDLORD")) is None:
            db.add(User(role="LANDLORD", name=settings.landlord_name,
                        phone=normalize_phone(settings.landlord_phone),
                        password_hash=hash_password(settings.landlord_password)))
            db.commit()


@asynccontextmanager
async def lifespan(_: FastAPI):
    migrate.upgrade()
    ensure_landlord()
    # Populate a fresh local database automatically; seed_demo is idempotent.
    from .seed_demo import seed_demo
    seed_demo()
    yield


app = FastAPI(title="Haven Heights API", version="0.1.0", lifespan=lifespan)
for module in (auth, me, tenants, leases, readings, bills, requests, announcements, notifications, dashboard,
               overview, devices):
    app.include_router(module.router)


@app.get("/health", tags=["meta"])
def health():
    return {"status": "ok"}
