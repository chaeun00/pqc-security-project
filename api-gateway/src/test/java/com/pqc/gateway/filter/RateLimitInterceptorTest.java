package com.pqc.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

class RateLimitInterceptorTest {

    @Test
    void cleanup_removesStaleIpEntry() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(10);
        try {
            // 5초 전 타임스탬프(1초 윈도우 초과) 수동 삽입
            Deque<Long> timestamps = new ArrayDeque<>();
            timestamps.addLast(System.nanoTime() - 5_000_000_000L);
            interceptor.ipTimestamps.put("10.0.0.1", timestamps);

            interceptor.cleanup();

            assertFalse(interceptor.ipTimestamps.containsKey("10.0.0.1"),
                    "만료된 타임스탬프만 가진 IP 엔트리는 cleanup 후 제거되어야 한다");
        } finally {
            interceptor.shutdown();
        }
    }

    @Test
    void preHandle_exceedsRateLimit_returns429() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(2);
        try {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("10.0.0.3");

            // 윈도우 내 타임스탬프 2개 수동 삽입 (한도 도달)
            Deque<Long> timestamps = new ArrayDeque<>();
            long now = System.nanoTime();
            timestamps.addLast(now);
            timestamps.addLast(now);
            interceptor.ipTimestamps.put("10.0.0.3", timestamps);

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean result = interceptor.preHandle(req, resp, new Object());

            assertFalse(result);
            assert resp.getStatus() == TOO_MANY_REQUESTS.value();
        } finally {
            interceptor.shutdown();
        }
    }

    @Test
    void cleanup_keepsActiveIpEntry() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(10);
        try {
            // 현재 시각 타임스탬프(윈도우 내) 수동 삽입
            Deque<Long> timestamps = new ArrayDeque<>();
            timestamps.addLast(System.nanoTime());
            interceptor.ipTimestamps.put("10.0.0.2", timestamps);

            interceptor.cleanup();

            assertTrue(interceptor.ipTimestamps.containsKey("10.0.0.2"),
                    "활성 타임스탬프를 가진 IP 엔트리는 cleanup 후 유지되어야 한다");
        } finally {
            interceptor.shutdown();
        }
    }
}
