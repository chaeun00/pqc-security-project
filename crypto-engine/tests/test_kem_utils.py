"""
crypto-engine KEM 유틸 단위 테스트
  - _derive_aes_key: HKDF 결정론적 도출 + 도메인 분리 검증
  - _cbom_insert: SQL 파라미터 검증
"""
import json
from unittest.mock import MagicMock

import pytest

from app.routers.kem import _cbom_insert, _derive_aes_key


# ── _derive_aes_key ──────────────────────────────────────────

def test_derive_aes_key_is_deterministic():
    """같은 shared_secret → 항상 동일한 32바이트 AES 키."""
    secret = b"\xab" * 32
    assert _derive_aes_key(secret) == _derive_aes_key(secret)


def test_derive_aes_key_length_is_32():
    """HKDF 출력은 정확히 32바이트여야 한다."""
    key = _derive_aes_key(b"\x01" * 32)
    assert len(key) == 32


def test_derive_aes_key_different_secrets_produce_different_keys():
    """다른 shared_secret → 다른 AES 키 (도메인 분리)."""
    key_a = _derive_aes_key(b"\xaa" * 32)
    key_b = _derive_aes_key(b"\xbb" * 32)
    assert key_a != key_b


# ── _cbom_insert ─────────────────────────────────────────────

def test_cbom_insert_calls_execute():
    """_cbom_insert가 cur.execute를 1회 호출하는지 확인."""
    cur = MagicMock()
    _cbom_insert(cur, "ML-KEM-768", "encrypt", 42)
    assert cur.execute.call_count == 1


def test_cbom_insert_sql_contains_insert():
    """실행된 SQL에 INSERT INTO cbom_assets가 포함되는지 확인."""
    cur = MagicMock()
    _cbom_insert(cur, "ML-KEM-768", "decrypt", 7)
    sql = cur.execute.call_args[0][0]
    assert "INSERT INTO cbom_assets" in sql


def test_cbom_insert_params_algorithm_and_event():
    """SQL 파라미터: algorithm_id와 asset JSON(event, key_id) 검증."""
    cur = MagicMock()
    _cbom_insert(cur, "ML-KEM-768", "encrypt", 99)
    params = cur.execute.call_args[0][1]
    assert params[0] == "ML-KEM-768"
    asset = json.loads(params[1])
    assert asset["event"] == "encrypt"
    assert asset["key_id"] == 99
