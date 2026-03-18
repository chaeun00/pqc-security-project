"""
algorithm_strategy 단위 테스트 (Day 8 Step 3)
  - 화이트리스트 정상 알고리즘 → 메타데이터 반환
  - 화이트리스트 외 알고리즘 → sys.exit(1)
  - KEM/DSA 화이트리스트 교차 오염 없음
"""
import pytest

from app.algorithm_strategy import (
    DSA_WHITELIST,
    KEM_WHITELIST,
    validate_algorithm,
)


# ── 정상 케이스 ────────────────────────────────────────────────────────────────

@pytest.mark.parametrize("alg", ["ML-KEM-512", "ML-KEM-768", "ML-KEM-1024"])
def test_kem_whitelist_valid(alg):
    meta = validate_algorithm(alg, KEM_WHITELIST, "KEM_ALGORITHM_ID")
    assert "security_level" in meta
    assert "sk_len" in meta
    assert "pk_len" in meta


@pytest.mark.parametrize("alg", ["ML-DSA-44", "ML-DSA-65", "ML-DSA-87"])
def test_dsa_whitelist_valid(alg):
    meta = validate_algorithm(alg, DSA_WHITELIST, "DSA_ALGORITHM_ID")
    assert "security_level" in meta


# ── 비정상 케이스 — startup fail-fast ─────────────────────────────────────────

def test_invalid_kem_algorithm_causes_exit():
    """INVALID-ALG → sys.exit(1) 확인 (인수조건 3)."""
    with pytest.raises(SystemExit) as exc_info:
        validate_algorithm("INVALID-ALG", KEM_WHITELIST, "KEM_ALGORITHM_ID")
    assert exc_info.value.code == 1


def test_invalid_dsa_algorithm_causes_exit():
    with pytest.raises(SystemExit) as exc_info:
        validate_algorithm("NOT-A-DSA", DSA_WHITELIST, "DSA_ALGORITHM_ID")
    assert exc_info.value.code == 1


# ── 교차 오염 방지 ─────────────────────────────────────────────────────────────

def test_kem_alg_not_accepted_as_dsa():
    """KEM 알고리즘을 DSA 화이트리스트에 넣으면 exit(1)."""
    with pytest.raises(SystemExit):
        validate_algorithm("ML-KEM-768", DSA_WHITELIST, "DSA_ALGORITHM_ID")


def test_dsa_alg_not_accepted_as_kem():
    """DSA 알고리즘을 KEM 화이트리스트에 넣으면 exit(1)."""
    with pytest.raises(SystemExit):
        validate_algorithm("ML-DSA-65", KEM_WHITELIST, "KEM_ALGORITHM_ID")


# ── 메타데이터 정합성 ──────────────────────────────────────────────────────────

def test_kem_768_metadata():
    meta = validate_algorithm("ML-KEM-768", KEM_WHITELIST, "KEM_ALGORITHM_ID")
    assert meta["security_level"] == 3
    assert meta["sk_len"] == 2400
    assert meta["pk_len"] == 1184


def test_dsa_65_metadata():
    meta = validate_algorithm("ML-DSA-65", DSA_WHITELIST, "DSA_ALGORITHM_ID")
    assert meta["security_level"] == 3
    assert meta["sk_len"] == 4032
    assert meta["pk_len"] == 1952


# ── 환경변수 오염 방어 (Step 5) ────────────────────────────────────────────────

def test_leading_trailing_whitespace_accepted():
    """공백 포함 알고리즘 ID → 정규화 후 허용."""
    meta = validate_algorithm("  ML-KEM-768  ", KEM_WHITELIST, "KEM_ALGORITHM_ID")
    assert meta["security_level"] == 3


def test_lowercase_accepted():
    """소문자 알고리즘 ID → 정규화 후 허용."""
    meta = validate_algorithm("ml-kem-768", KEM_WHITELIST, "KEM_ALGORITHM_ID")
    assert meta["security_level"] == 3


def test_empty_string_causes_exit():
    """빈 문자열 → exit(1)."""
    with pytest.raises(SystemExit) as exc_info:
        validate_algorithm("", KEM_WHITELIST, "KEM_ALGORITHM_ID")
    assert exc_info.value.code == 1


def test_whitespace_only_causes_exit():
    """공백만 있는 문자열 → exit(1)."""
    with pytest.raises(SystemExit) as exc_info:
        validate_algorithm("   ", KEM_WHITELIST, "KEM_ALGORITHM_ID")
    assert exc_info.value.code == 1
