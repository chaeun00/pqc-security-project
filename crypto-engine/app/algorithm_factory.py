"""Algorithm Agility: ALGORITHM_ID 환경변수로 KEM·DSA 알고리즘 교체 (Step 1-B)"""
import os

import oqs

KEM_ALGORITHM = os.getenv("KEM_ALGORITHM_ID", "ML-KEM-768")
DSA_ALGORITHM = os.getenv("DSA_ALGORITHM_ID", "ML-DSA-65")


def get_kem() -> oqs.KeyEncapsulation:
    """설정된 KEM 알고리즘 인스턴스 반환 (context manager 사용 권장)"""
    return oqs.KeyEncapsulation(KEM_ALGORITHM)


def get_signature() -> oqs.Signature:
    """설정된 DSA 알고리즘 인스턴스 반환 (context manager 사용 권장)"""
    return oqs.Signature(DSA_ALGORITHM)
