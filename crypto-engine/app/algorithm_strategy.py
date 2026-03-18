"""Algorithm Agility: 화이트리스트 검증 + 알고리즘 메타데이터 (Day 8 Step 1)

환경변수 KEM_ALGORITHM_ID / DSA_ALGORITHM_ID 교체만으로 재빌드 없이 알고리즘 전환.
화이트리스트 외 값은 startup 단계에서 sys.exit(1) (fail-fast).
"""
import os
import sys

# ── 지원 알고리즘 화이트리스트 + per-algorithm 메타데이터 ────────────────────────
# security_level: NIST PQC 보안 레벨 (1/3/5)
# sk_len / pk_len: 바이트 단위 키 크기 (런타임 유효성 검사용)
KEM_WHITELIST: dict[str, dict] = {
    "ML-KEM-512":  {"security_level": 1, "sk_len": 1632, "pk_len": 800},
    "ML-KEM-768":  {"security_level": 3, "sk_len": 2400, "pk_len": 1184},
    "ML-KEM-1024": {"security_level": 5, "sk_len": 3168, "pk_len": 1568},
}

DSA_WHITELIST: dict[str, dict] = {
    "ML-DSA-44": {"security_level": 2, "sk_len": 2560, "pk_len": 1312},
    "ML-DSA-65": {"security_level": 3, "sk_len": 4032, "pk_len": 1952},
    "ML-DSA-87": {"security_level": 5, "sk_len": 4896, "pk_len": 2592},
}


def validate_algorithm(algorithm_id: str, whitelist: dict, env_key: str) -> dict:
    """화이트리스트 검증 — 실패 시 sys.exit(1) (startup fail-fast).

    입력값은 .strip().upper() 정규화 후 비교 (공백·소문자 오염 방어).

    Returns:
        해당 알고리즘의 메타데이터 dict.
    """
    normalized = algorithm_id.strip().upper()
    if normalized not in whitelist:
        print(
            f"[FATAL] {env_key}='{algorithm_id}' is not allowed. "
            f"Supported: {list(whitelist.keys())}",
            file=sys.stderr,
        )
        sys.exit(1)
    return whitelist[normalized]


# ── 모듈 로드 시 1회 검증 (startup fail-fast) ───────────────────────────────────
# .strip().upper() 정규화: 환경변수 공백·소문자 오염 방어
KEM_ALGORITHM: str = os.getenv("KEM_ALGORITHM_ID", "ML-KEM-768").strip().upper()
DSA_ALGORITHM: str = os.getenv("DSA_ALGORITHM_ID", "ML-DSA-65").strip().upper()

KEM_META: dict = validate_algorithm(KEM_ALGORITHM, KEM_WHITELIST, "KEM_ALGORITHM_ID")
DSA_META: dict = validate_algorithm(DSA_ALGORITHM, DSA_WHITELIST, "DSA_ALGORITHM_ID")
