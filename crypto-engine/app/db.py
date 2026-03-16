import os
import base64
from contextlib import contextmanager

import psycopg2
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def _get_wrap_key() -> bytes:
    raw = base64.b64decode(os.environ["KEM_WRAP_KEY"])
    if len(raw) != 32:
        raise ValueError("KEM_WRAP_KEY must decode to exactly 32 bytes")
    return raw


def wrap_secret(secret_key_bytes: bytes) -> tuple[bytes, bytes]:
    """AES-256-GCM으로 secret_key를 래핑한다.
    Returns (wrapped, iv)
      wrapped = ciphertext || 16-byte auth tag (AESGCM.encrypt 표준 출력)
      iv      = 12-byte random nonce
    """
    iv = os.urandom(12)
    aesgcm = AESGCM(_get_wrap_key())
    wrapped = aesgcm.encrypt(iv, secret_key_bytes, None)
    return wrapped, iv


def unwrap_secret(wrapped: bytes, iv: bytes) -> bytes:
    """AES-256-GCM 래핑 해제."""
    aesgcm = AESGCM(_get_wrap_key())
    return aesgcm.decrypt(iv, wrapped, None)


@contextmanager
def db_cursor():
    conn = psycopg2.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT", "5432")),
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
    )
    try:
        with conn.cursor() as cur:
            yield conn, cur
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
