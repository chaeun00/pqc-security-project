package com.pqc.gateway.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * CryptoEngineClient 전용 Feign RequestInterceptor — 알고리즘 ID 헤더 주입 (Day 8 rev)
 * @Component 제거 → CryptoEngineClientConfig에서만 빈으로 등록 (범위 한정)
 * X-Kem-Algorithm-Id / X-Dsa-Algorithm-Id
 */
public class AlgorithmFeignInterceptor implements RequestInterceptor {

    private final CryptoAlgorithmProperties algorithmProperties;

    public AlgorithmFeignInterceptor(CryptoAlgorithmProperties algorithmProperties) {
        this.algorithmProperties = algorithmProperties;
    }

    @Override
    public void apply(RequestTemplate template) {
        // per-request @RequestHeader 우선 — 이미 설정된 경우 env-var 기본값으로 덮어쓰지 않음
        if (!template.headers().containsKey("X-Kem-Algorithm-Id")) {
            template.header("X-Kem-Algorithm-Id", algorithmProperties.getKemId());
        }
        template.header("X-Dsa-Algorithm-Id", algorithmProperties.getDsaId());
    }
}
