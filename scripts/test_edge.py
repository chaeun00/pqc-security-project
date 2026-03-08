#!/usr/bin/env python3
"""
crypto-engine 엣지 케이스 테스트 (컨테이너 내부에서 실행)
Step 1-B Final: 422/400 분기, 빈 message, 에러 마스킹 검증

사용법: /venv/bin/python /tmp/test_edge.py
        (ci.yml integration-test 잡에서 docker exec 로 호출)
"""
import base64
import json
import sys
import urllib.error
import urllib.request

BASE_URL = "http://localhost:8000"
TIMEOUT = 10


def post_expect_error(path: str, body: dict, expected_status: int) -> dict:
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        urllib.request.urlopen(req, timeout=TIMEOUT)
        raise AssertionError(
            f"FAIL: {path} — {expected_status} 예상, 200 반환됨"
        )
    except urllib.error.HTTPError as e:
        assert e.code == expected_status, \
            f"FAIL: {path} — 예상 {expected_status}, 실제 {e.code}"
        return json.loads(e.read())


def main() -> None:
    # ── [1] DSA sign 빈 message → 422 ────────────────────
    print("==> [1/5] DSA sign 빈 message → 422")
    err = post_expect_error("/dsa/sign", {"message": ""}, 422)
    print(f"  PASS ✓ (422, detail: {err.get('detail', '')!r})")

    # ── [2] KEM encrypt public_key 필드 누락 → 422 ────────
    print("==> [2/5] KEM encrypt public_key 누락 → 422")
    post_expect_error("/kem/encrypt", {}, 422)
    print("  PASS ✓ (422)")

    # ── [3] KEM encrypt 잘못된 base64 → 422 ───────────────
    print("==> [3/5] KEM encrypt 잘못된 base64 → 422")
    post_expect_error("/kem/encrypt", {"public_key": "not!!valid==base64"}, 422)
    print("  PASS ✓ (422)")

    # ── [4] KEM encrypt 비호환 공개키 → 400 + 에러 마스킹 ──
    print("==> [4/5] KEM encrypt 비호환 공개키 → 400, 에러 마스킹")
    err = post_expect_error(
        "/kem/encrypt",
        {"public_key": base64.b64encode(b"wrong_key_bytes").decode()},
        400,
    )
    detail = str(err.get("detail", ""))
    assert "traceback" not in detail.lower(), \
        f"FAIL: traceback 노출 — {detail!r}"
    assert "캡슐화 오류" in detail, \
        f"FAIL: 에러 메시지 불일치 — {detail!r}"
    print(f"  PASS ✓ (400, masked: {detail!r})")

    # ── [5] DSA sign 65537자 message → 422 (DoS 제한) ──
    print("==> [5/5] DSA sign 65537자 message → 422 (max_length=65536 제한)")
    post_expect_error("/dsa/sign", {"message": "A" * 65537}, 422)
    print("  PASS ✓ (422)")

    print("==> 엣지 케이스 테스트 모두 통과 ✓")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
