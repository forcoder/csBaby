import hashlib
import json
import secrets
import time
from typing import Optional

import jwt


class AuthService:
    def __init__(self, jwt_secret: str, jwt_expire_days: int = 30):
        self._secret = jwt_secret
        self._expire_days = jwt_expire_days

    def generate_token(self, user_id: str) -> str:
        payload = {
            "user_id": user_id,
            "exp": int(time.time()) + self._expire_days * 86400,
            "iat": int(time.time()),
        }
        return jwt.encode(payload, self._secret, algorithm="HS256")

    def verify_token(self, token: str) -> Optional[str]:
        try:
            payload = jwt.decode(token, self._secret, algorithms=["HS256"])
            return payload.get("user_id")
        except (jwt.ExpiredSignatureError, jwt.InvalidTokenError):
            return None

    @staticmethod
    def hash_password(password: str, salt: Optional[str] = None) -> tuple:
        """Hash password with salt using SHA-256. Returns (hash, salt)."""
        if salt is None:
            salt = secrets.token_hex(16)
        pw_hash = hashlib.sha256(f"{salt}{password}".encode()).hexdigest()
        return pw_hash, salt

    @staticmethod
    def verify_password(password: str, salt: str, password_hash: str) -> bool:
        """Verify password against stored hash."""
        pw_hash = hashlib.sha256(f"{salt}{password}".encode()).hexdigest()
        return pw_hash == password_hash
