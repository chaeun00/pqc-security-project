import json
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.algorithm_strategy import DSA_ALGORITHM, KEM_ALGORITHM
from app.routers import dsa, kem

logger = logging.getLogger(__name__)

_cbom_inserted: bool = False  # startup CBOM INSERT 성공 여부 (GET /health 에서 노출)


def _cbom_startup_insert() -> None:
    """서버 기동 시 KEM/DSA 알고리즘 전환 이벤트를 cbom_assets에 1회 INSERT.
    DB 연결 실패는 경고 로그만 남기고 기동을 막지 않는다.
    """
    global _cbom_inserted
    try:
        from app.db import db_cursor  # 로컬 import — DB 없는 테스트 환경 보호

        with db_cursor() as (_, cur):
            for algo, asset_type in [(KEM_ALGORITHM, "KEM"), (DSA_ALGORITHM, "DSA")]:
                cur.execute(
                    """
                    INSERT INTO cbom_assets (algorithm_id, asset, source, risk_level)
                    VALUES (%s, %s, 'auto', 'NONE')
                    """,
                    (algo, json.dumps({"event": "algorithm_transition", "type": asset_type, "phase": "startup"})),
                )
        _cbom_inserted = True
        logger.info("CBOM startup: algorithm_transition recorded (KEM=%s, DSA=%s)", KEM_ALGORITHM, DSA_ALGORITHM)
    except Exception as exc:  # noqa: BLE001
        logger.warning("CBOM startup insert skipped (DB unavailable?): %s", exc, exc_info=True)


@asynccontextmanager
async def lifespan(application: FastAPI):
    _cbom_startup_insert()
    yield


app = FastAPI(title="Crypto Engine", version="1.0.0", lifespan=lifespan)

app.include_router(kem.router, prefix="/kem", tags=["KEM"])
app.include_router(dsa.router, prefix="/dsa", tags=["DSA"])


@app.get("/health")
def health():
    """docker-compose healthcheck 전용 — 최소 응답."""
    return {"status": "ok"}


@app.get("/health/detail")
def health_detail():
    """내부 진단용 — cbom_inserted 등 상세 startup 상태 포함."""
    return {"status": "ok", "cbom_inserted": _cbom_inserted}
