#!/usr/bin/env python3
"""
crypto-engine HTTP 왕복 테스트 (컨테이너 내부에서 실행)
Day 6 인터페이스 기준:
  - KEM: /kem/init → /kem/encrypt (key_id 기반, shared_secret 미반환)
  - KEM: /kem/keygen, /kem/decrypt → 410 Gone
  - DSA: sign → verify valid:true

사용법: /venv/bin/python /tmp/http_test.py
        (ci.yml integration-test 잡에서 docker exec 로 호출)
"""
import json
import sys
import time
import urllib.error
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


def post_expect_410(path: str, body: dict | None = None) -> None:
    data = json.dumps(body or {}).encode()
    req = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        urllib.request.urlopen(req, timeout=TIMEOUT)
        raise AssertionError(f"FAIL: {path} — 410 예상, 200 반환됨")
    except urllib.error.HTTPError as e:
        assert e.code == 410, f"FAIL: {path} — 예상 410, 실제 {e.code}"


def main() -> None:
    # ── /health ───────────────────────────────────────────
    print("==> [1/6] GET /health")
    resp = get("/health")
    assert resp.get("status") == "ok", f"FAIL: status={resp.get('status')}"
    print("  PASS ✓ (status: ok)")

    # ── /kem/keygen → 410 ─────────────────────────────────
    print("==> [2/6] POST /kem/keygen → 410 Gone")
    post_expect_410("/kem/keygen")
    print("  PASS ✓ (410 Gone)")

    # ── KEM 왕복 (init → encrypt) ─────────────────────────
    print("==> [3/6] KEM /kem/init → key_id 반환 (secret_key 미포함)")
    init = post("/kem/init")
    assert "key_id" in init, f"FAIL: key_id 없음 — {init}"
    assert "secret_key" not in init, "FAIL: secret_key 노출됨"
    key_id = init["key_id"]
    print(f"  PASS ✓ (key_id={key_id}, secret_key 미포함)")

    print("==> [4/6] KEM /kem/encrypt → ciphertext 반환 (shared_secret 미포함)")
    enc = post("/kem/encrypt", {"key_id": key_id})
    assert "ciphertext" in enc, f"FAIL: ciphertext 없음 — {enc}"
    assert "shared_secret" not in enc, "FAIL: shared_secret 노출됨"
    print("  PASS ✓ (ciphertext 존재, shared_secret 미포함)")

    # ── /kem/decrypt → 410 ────────────────────────────────
    print("==> [5/6] POST /kem/decrypt → 410 Gone (decryption oracle 차단)")
    post_expect_410("/kem/decrypt", {"secret_key": "dGVzdA==", "ciphertext": "dGVzdA=="})
    print("  PASS ✓ (410 Gone)")

    # ── DSA 왕복 ──────────────────────────────────────────
    print("==> [6/6] DSA sign → verify (valid:true) + secret_key 미포함")
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
        "FAIL: DSA sign 응답에 secret_key 포함됨"
    print("  DSA PASS ✓ (valid:true, secret_key 미포함)")

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
