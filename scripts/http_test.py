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


def get(path: str) -> dict:
    r = urllib.request.urlopen(f"{BASE_URL}{path}")
    return json.loads(r.read())


def post(path: str, body: dict | None = None) -> dict:
    data = json.dumps(body or {}).encode()
    req = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    r = urllib.request.urlopen(req)
    return json.loads(r.read())


def main() -> None:
    # ── /health ───────────────────────────────────────────
    print("==> [1/3] GET /health")
    resp = get("/health")
    assert resp.get("status") == "ok", f"FAIL: status={resp.get('status')}"
    print("  PASS ✓ (status: ok)")

    # ── KEM 왕복 ──────────────────────────────────────────
    print("==> [2/3] KEM encrypt → decrypt (shared_secret 일치)")
    enc = post("/kem/encrypt")
    dec = post("/kem/decrypt", {
        "secret_key": enc["secret_key"],
        "ciphertext": enc["ciphertext"],
    })
    assert enc["shared_secret"] == dec["shared_secret"], \
        "FAIL: shared_secret 불일치 (encrypt != decrypt)"
    print("  KEM PASS ✓ (shared_secret 일치)")

    # ── DSA 왕복 ──────────────────────────────────────────
    print("==> [3/3] DSA sign → verify (valid:true)")
    msg = f"pqc-verify-{int(time.time())}"
    sign = post("/dsa/sign", {"message": msg})
    verify = post("/dsa/verify", {
        "message": msg,
        "signature": sign["signature"],
        "public_key": sign["public_key"],
    })
    assert verify.get("valid") is True, \
        f"FAIL: DSA verify valid={verify.get('valid')}"
    print("  DSA PASS ✓ (valid: true)")

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
