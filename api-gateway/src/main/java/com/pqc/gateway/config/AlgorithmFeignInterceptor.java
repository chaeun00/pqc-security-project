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
        template.header("X-Kem-Algorithm-Id", algorithmProperties.getKemId());
        template.header("X-Dsa-Algorithm-Id", algorithmProperties.getDsaId());
    }
}
