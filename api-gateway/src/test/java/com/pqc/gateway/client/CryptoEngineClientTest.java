package com.pqc.gateway.client;

import com.pqc.gateway.dto.DsaSignRequest;
import com.pqc.gateway.dto.DsaSignResponse;
import com.pqc.gateway.dto.DsaVerifyRequest;
import com.pqc.gateway.dto.DsaVerifyResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.web.server.ResponseStatusException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// FeignCircuitBreakerConfig 에 의해 CB 이름 = "crypto-engine" (client 명 단일 사용)
// WireMock은 HTTP만 지원 → allow-http=true 로 HTTPS 검증 비활성화
// circuitbreaker.enabled 를 명시적으로 설정해야 @SpringBootTest 컨텍스트에서 CB가 활성화됨
@SpringBootTest(properties = {
        "crypto.engine.url=http://localhost:${wiremock.server.port}",
        "crypto.engine.allow-http=true",
        "spring.cloud.openfeign.circuitbreaker.enabled=true",
        "resilience4j.circuitbreaker.instances.crypto-engine.sliding-window-size=2",
        "resilience4j.circuitbreaker.instances.crypto-engine.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.crypto-engine.failure-rate-threshold=50"
})
@AutoConfigureWireMock(port = 0)
class CryptoEngineClientTest {

    @Autowired
    CryptoEngineClient client;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        resetAllRequests();
        circuitBreakerRegistry.circuitBreaker("crypto-engine").reset();
    }

    @Test
    void sign_success_returnsResponse() {
        stubFor(post(urlEqualTo("/dsa/sign"))
                .willReturn(okJson("""
                        {"algorithm":"Dilithium2","message":"hello","signature":"abc","public_key":"xyz"}
                        """)));

        DsaSignResponse resp = client.sign(new DsaSignRequest("hello"));

        assertThat(resp.algorithm()).isEqualTo("Dilithium2");
        assertThat(resp.message()).isEqualTo("hello");
        assertThat(resp.signature()).isEqualTo("abc");
        assertThat(resp.publicKey()).isEqualTo("xyz");
    }

    @Test
    void verify_success_returnsResponse() {
        stubFor(post(urlEqualTo("/dsa/verify"))
                .willReturn(okJson("""
                        {"algorithm":"Dilithium2","valid":true}
                        """)));

        DsaVerifyResponse resp = client.verify(new DsaVerifyRequest("hello", "abc", "xyz"));

        assertThat(resp.algorithm()).isEqualTo("Dilithium2");
        assertThat(resp.valid()).isTrue();
    }

    @Test
    void sign_circuitBreaker_open_throws503() {
        // crypto-engine 500 응답 → FeignException → CB failure 기록 (CB closed이면 fallback 호출됨)
        stubFor(post(urlEqualTo("/dsa/sign")).willReturn(serverError()));

        // window=2, min=2, threshold=50% → 2회 실패(100%) → CB Open
        // CB closed 상태에서도 fallback이 호출되어 ResponseStatusException(503) 반환
        assertThrows(ResponseStatusException.class, () -> client.sign(new DsaSignRequest("t1")));
        assertThrows(ResponseStatusException.class, () -> client.sign(new DsaSignRequest("t2")));

        assertThat(circuitBreakerRegistry.circuitBreaker("crypto-engine").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // CB Open → Fallback → ResponseStatusException(503)
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.sign(new DsaSignRequest("t3")));
        assertThat(ex.getStatusCode().value()).isEqualTo(503);
    }
}
