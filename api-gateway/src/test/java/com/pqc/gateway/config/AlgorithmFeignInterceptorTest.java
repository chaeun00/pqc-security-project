package com.pqc.gateway.config;

import com.pqc.gateway.service.AlgorithmHotSwapService;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlgorithmFeignInterceptorTest {

    private AlgorithmFeignInterceptor buildInterceptor(String kemId, String dsaId) {
        CryptoAlgorithmProperties props = new CryptoAlgorithmProperties();
        props.setKemId(kemId);
        props.setDsaId(dsaId);
        return new AlgorithmFeignInterceptor(new AlgorithmHotSwapService(props));
    }

    // 헤더 미설정 → env-var 기본값으로 KEM·DSA 모두 주입
    @Test
    void apply_noHeaderPreset_addsDefaultKemAndDsa() {
        AlgorithmFeignInterceptor interceptor = buildInterceptor("ML-KEM-768", "ML-DSA-65");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Kem-Algorithm-Id")).contains("ML-KEM-768");
        assertThat(template.headers().get("X-Dsa-Algorithm-Id")).contains("ML-DSA-65");
    }

    // 헤더 사전 설정(per-request 오버라이드) → KEM 덮어쓰기 금지, DSA는 정상 추가
    @Test
    void apply_kemHeaderPreset_doesNotOverrideKem() {
        AlgorithmFeignInterceptor interceptor = buildInterceptor("ML-KEM-768", "ML-DSA-65");
        RequestTemplate template = new RequestTemplate();
        template.header("X-Kem-Algorithm-Id", "ML-KEM-1024");

        interceptor.apply(template);

        assertThat(template.headers().get("X-Kem-Algorithm-Id")).contains("ML-KEM-1024");
        assertThat(template.headers().get("X-Dsa-Algorithm-Id")).contains("ML-DSA-65");
    }

    // DSA 헤더는 항상 env-var 값으로 덮어씀 (per-request 오버라이드 없음)
    @Test
    void apply_dsaHeaderAlwaysSet() {
        AlgorithmFeignInterceptor interceptor = buildInterceptor("ML-KEM-768", "ML-DSA-87");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Dsa-Algorithm-Id")).contains("ML-DSA-87");
    }
}
