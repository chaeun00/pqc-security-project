package com.pqc.gateway.config;

import com.pqc.gateway.service.AlgorithmHotSwapService;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * CryptoEngineClient 전용 Feign RequestInterceptor — 알고리즘 ID 헤더 주입 (Day 8 rev)
 * Day 10: CryptoAlgorithmProperties → AlgorithmHotSwapService 로 교체 (Hot-swap 지원)
 * @Component 제거 → CryptoEngineClientConfig에서만 빈으로 등록 (범위 한정)
 * X-Kem-Algorithm-Id / X-Dsa-Algorithm-Id
 */
public class AlgorithmFeignInterceptor implements RequestInterceptor {

    private final AlgorithmHotSwapService hotSwapService;

    public AlgorithmFeignInterceptor(AlgorithmHotSwapService hotSwapService) {
        this.hotSwapService = hotSwapService;
    }

    @Override
    public void apply(RequestTemplate template) {
        // per-request @RequestHeader 우선 — 이미 설정된 경우 hot-swap 기본값으로 덮어쓰지 않음
        if (!template.headers().containsKey("X-Kem-Algorithm-Id")) {
            template.header("X-Kem-Algorithm-Id", hotSwapService.getKemId());
        }
        template.header("X-Dsa-Algorithm-Id", hotSwapService.getDsaId());
    }
}
