package com.pqc.gateway.logging;

import feign.Logger;
import feign.Response;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Feign API 호출 latency 기록용 커스텀 Logger.
 * feign이 제공하는 elapsedTime(ms)을 INFO 레벨로 출력.
 * → 로그 패턴: [FEIGN-LATENCY] {configKey} status={status} latency={ms}ms
 *
 * 확인 방법:
 *   1. 로그: "FEIGN-LATENCY" 키워드로 grep
 *   2. 메트릭: GET /actuator/metrics/feign.client.requests
 */
public class FeignLatencyLogger extends Logger {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(FeignLatencyLogger.class);

    @Override
    protected void log(String configKey, String format, Object... args) {
        // 표준 Feign 디버그 로그는 TRACE로 내림 (노이즈 억제)
        if (log.isTraceEnabled()) {
            log.trace("[FEIGN] {} {}", configKey, String.format(format, args));
        }
    }

    @Override
    protected Response logAndRebufferResponse(
            String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        log.info("[FEIGN-LATENCY] {} status={} latency={}ms",
                configKey, response.status(), elapsedTime);
        return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
    }
}
