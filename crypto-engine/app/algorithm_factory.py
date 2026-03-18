"""Algorithm Agility: ALGORITHM_ID 환경변수로 KEM·DSA 알고리즘 교체 (Step 1-B / Day 8)"""
import base64
import os

import oqs

from app.algorithm_strategy import DSA_META, DSA_ALGORITHM, KEM_ALGORITHM  # noqa: F401 (re-export)

_secret_key_b64 = os.getenv("DSA_SECRET_KEY_B64")
_public_key_b64 = os.getenv("DSA_PUBLIC_KEY_B64")

if _secret_key_b64 and _public_key_b64:
    # 외부 주입 키쌍 — --preload 없이도 worker 간 키 일치 (메모리 실험용)
    _DSA_SECRET_KEY: bytes = base64.b64decode(_secret_key_b64)
    DSA_PUBLIC_KEY: bytes = base64.b64decode(_public_key_b64)
    # fail-fast: 잘못된 키 주입 시 기동 즉시 중단 (알고리즘별 규격 크기는 DSA_META에서 참조)
    _EXPECTED_SK_LEN = DSA_META["sk_len"]
    _EXPECTED_PK_LEN = DSA_META["pk_len"]
    if len(_DSA_SECRET_KEY) != _EXPECTED_SK_LEN:
        raise ValueError(
            f"DSA_SECRET_KEY_B64 크기 오류: expected={_EXPECTED_SK_LEN}, got={len(_DSA_SECRET_KEY)}"
        )
    if len(DSA_PUBLIC_KEY) != _EXPECTED_PK_LEN:
        raise ValueError(
            f"DSA_PUBLIC_KEY_B64 크기 오류: expected={_EXPECTED_PK_LEN}, got={len(DSA_PUBLIC_KEY)}"
        )
else:
    # 기본: 모듈 로드 시 1회 생성 (--preload 환경에서 fork()로 worker 상속)
    with oqs.Signature(DSA_ALGORITHM) as _dsa_init:
        DSA_PUBLIC_KEY: bytes = _dsa_init.generate_keypair()
        _DSA_SECRET_KEY: bytes = _dsa_init.export_secret_key()


def get_kem() -> oqs.KeyEncapsulation:
    """설정된 KEM 알고리즘 인스턴스 반환 (context manager 사용 권장)"""
    return oqs.KeyEncapsulation(KEM_ALGORITHM)


def get_signature() -> oqs.Signature:
    """서명용 DSA 인스턴스 반환 (persistent secret key, context manager 사용 권장)"""
    return oqs.Signature(DSA_ALGORITHM, _DSA_SECRET_KEY)
