#!/usr/bin/env python3
"""
crypto-engine HTTP 왕복 테스트 (컨테이너 내부에서 실행)
Step 1-B rev.10 인수조건 2:
  - KEM encrypt→decrypt shared_secret 일치
  - DSA sign→verify valid:true

사용법: /venv/bin/python /tmp/http_test.py
        (scripts/verify-http.sh 가 docker exec 로 호출)
"""
import json
import sys
import time
import urllib.request

BASE_URL = "http://localhost:8000"


TIMEOUT = 10


def get(path: str) -> dict:
    r = urllib.request.urlopen(f"{BASE_URL}{path}", timeout=TIMEOUT)
    return json.loads(r.read())


def post(path: str, body: dict | None = None) -> dict:
    data = json.dumps(body or {}).encode()
    req = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    r = urllib.request.urlopen(req, timeout=TIMEOUT)
    return json.loads(r.read())


def main() -> None:
    # ── /health ───────────────────────────────────────────
    print("==> [1/3] GET /health")
    resp = get("/health")
    assert resp.get("status") == "ok", f"FAIL: status={resp.get('status')}"
    print("  PASS ✓ (status: ok)")

    # ── KEM 왕복 ──────────────────────────────────────────
    print("==> [2/5] KEM keygen → encrypt → decrypt (shared_secret 일치)")
    keygen = post("/kem/keygen")
    enc = post("/kem/encrypt", {"public_key": keygen["public_key"]})
    dec = post("/kem/decrypt", {
        "secret_key": keygen["secret_key"],
        "ciphertext": enc["ciphertext"],
    })
    assert enc["shared_secret"] == dec["shared_secret"], \
        "FAIL: shared_secret 불일치 (encrypt != decrypt)"
    print("  KEM PASS ✓ (shared_secret 일치)")

    # ── KEM secret_key 미포함 증명 ────────────────────────
    print("==> [3/5] KEM encrypt 응답 secret_key 미포함 (보안 결함 수정 증명)")
    assert "secret_key" not in enc, \
        "FAIL: KEM encrypt 응답에 secret_key 포함됨 (비밀키 노출)"
    print("  PASS ✓ (secret_key 미포함)")

    # ── DSA 왕복 ──────────────────────────────────────────
    print("==> [4/5] DSA sign → verify (valid:true) + secret_key 미포함")
    msg = f"pqc-verify-{int(time.time())}"
    sign = post("/dsa/sign", {"message": msg})
    verify = post("/dsa/verify", {
        "message": msg,
        "signature": sign["signature"],
        "public_key": sign["public_key"],
    })
    assert verify.get("valid") is True, \
        f"FAIL: DSA verify valid={verify.get('valid')}"
    assert "secret_key" not in sign, \
        "FAIL: DSA sign 응답에 secret_key 포함됨 (비밀키 노출)"
    print("  DSA PASS ✓ (valid:true, secret_key 미포함)")

    # ── DSA 키 영속성 ────────────────────────────────────
    print("==> [5/5] DSA 키 영속성 (연속 sign → 동일 public_key)")
    sign2 = post("/dsa/sign", {"message": f"pqc-verify2-{int(time.time())}"})
    assert sign["public_key"] == sign2["public_key"], \
        "FAIL: DSA 키 영속성 깨짐 (public_key 불일치)"
    print("  PASS ✓ (public_key 일치)")

    print("==> HTTP 왕복 테스트 모두 통과 ✓")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
