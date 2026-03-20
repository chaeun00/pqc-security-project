package com.pqc.gateway.config;

import com.pqc.gateway.service.AlgorithmHotSwapService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * CryptoEngineClient 전용 Feign 설정 (Day 8 rev)
 * AlgorithmFeignInterceptor를 이 클라이언트 범위에만 등록.
 * Day 10: CryptoAlgorithmProperties → AlgorithmHotSwapService (Hot-swap 지원)
 * 주의: @Configuration 미사용 — @FeignClient(configuration=...) 으로만 로드되어야 함
 *        (전역 Spring 컨텍스트에 등록되면 모든 Feign 클라이언트에 적용됨)
 */
public class CryptoEngineClientConfig {

    @Bean
    public RequestInterceptor algorithmHeaderInterceptor(AlgorithmHotSwapService hotSwapService) {
        return new AlgorithmFeignInterceptor(hotSwapService);
    }
}
