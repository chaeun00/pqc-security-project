package com.pqc.gateway.filter;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * IP 기반 슬라이딩 윈도우 Rate Limiter (초 단위).
 * 임계치 초과 시 429 반환.
 * 60초 주기로 비활성 IP 엔트리를 정리한다.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final long WINDOW_NS = 1_000_000_000L; // 1초(nanosecond)

    private final int maxRequestsPerSecond;
    final ConcurrentHashMap<String, Deque<Long>> ipTimestamps = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public RateLimitInterceptor(int maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanup, 60, 60, TimeUnit.SECONDS);
    }

    /** 1초 윈도우 만료 후 빈 Deque 엔트리를 제거한다. 단위 테스트에서도 직접 호출 가능. */
    void cleanup() {
        long nowNano = System.nanoTime();
        ipTimestamps.forEach((ip, timestamps) -> {
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && nowNano - timestamps.peekFirst() > WINDOW_NS) {
                    timestamps.pollFirst();
                }
                if (timestamps.isEmpty()) {
                    ipTimestamps.remove(ip, timestamps);
                }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String ip = request.getRemoteAddr();
        long nowNano = System.nanoTime();

        Deque<Long> timestamps = ipTimestamps.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // 1초 윈도우 바깥 타임스탬프 제거
            while (!timestamps.isEmpty() && nowNano - timestamps.peekFirst() > WINDOW_NS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequestsPerSecond) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
                return false;
            }
            timestamps.addLast(nowNano);
        }

        return true;
    }
}
