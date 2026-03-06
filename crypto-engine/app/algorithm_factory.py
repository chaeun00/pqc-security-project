"""Algorithm Agility: ALGORITHM_ID 환경변수로 KEM·DSA 알고리즘 교체 (Step 1-B)"""
import os

import oqs

KEM_ALGORITHM = os.getenv("KEM_ALGORITHM_ID", "ML-KEM-768")
DSA_ALGORITHM = os.getenv("DSA_ALGORITHM_ID", "ML-DSA-65")

# DSA 키 영속성: 서버 시작 시 1회 생성 (요청마다 키쌍 재생성 방지)
with oqs.Signature(DSA_ALGORITHM) as _dsa_init:
    DSA_PUBLIC_KEY: bytes = _dsa_init.generate_keypair()
    _DSA_SECRET_KEY: bytes = _dsa_init.export_secret_key()


def get_kem() -> oqs.KeyEncapsulation:
    """설정된 KEM 알고리즘 인스턴스 반환 (context manager 사용 권장)"""
    return oqs.KeyEncapsulation(KEM_ALGORITHM)


def get_signature() -> oqs.Signature:
    """서명용 DSA 인스턴스 반환 (persistent secret key, context manager 사용 권장)"""
    return oqs.Signature(DSA_ALGORITHM, _DSA_SECRET_KEY)
