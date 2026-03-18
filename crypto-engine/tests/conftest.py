"""
pytest conftest — 테스트 격리 픽스처 (Day 8 Step 4)

sys.exit 함정 방어:
  algorithm_strategy 모듈은 임포트 시 os.getenv로 알고리즘 ID를 읽고 validate_algorithm을
  호출한다. 환경변수가 잘못 설정된 상태에서 모듈이 (재)임포트되면 sys.exit(1)이 발생한다.

  - autouse fixture `valid_algorithm_env`: 모든 테스트 전에 유효한 기본값 보장
  - algorithm_strategy 모듈 자체를 다른 env로 재로드하는 테스트는 아래 패턴 사용:
      import importlib, app.algorithm_strategy as _m
      monkeypatch.setenv("KEM_ALGORITHM_ID", "ML-KEM-512")
      importlib.reload(_m)  # 재로드 후 반드시 원상복구 필요
"""
import pytest


@pytest.fixture(autouse=True)
def valid_algorithm_env(monkeypatch):
    """모든 테스트에서 KEM/DSA 알고리즘 환경변수 유효값 보장."""
    monkeypatch.setenv("KEM_ALGORITHM_ID", "ML-KEM-768")
    monkeypatch.setenv("DSA_ALGORITHM_ID", "ML-DSA-65")
