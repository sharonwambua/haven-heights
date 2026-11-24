from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from .config import settings


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode(), hashed.encode())
    except ValueError:
        return False


def create_token(user_id: int) -> str:
    exp = datetime.now(timezone.utc) + timedelta(minutes=settings.token_minutes)
    return jwt.encode({"sub": str(user_id), "exp": exp}, settings.secret_key, algorithm="HS256")


def decode_token(token: str) -> int | None:
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
        return int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        return None


def normalize_phone(phone: str) -> str:
    """Store Kenyan numbers in one shape: 07XXXXXXXX / 01XXXXXXXX."""
    digits = "".join(ch for ch in phone if ch.isdigit())
    if digits.startswith("254") and len(digits) == 12:
        digits = "0" + digits[3:]
    elif len(digits) == 9 and digits[0] in "71":
        digits = "0" + digits
    return digits
