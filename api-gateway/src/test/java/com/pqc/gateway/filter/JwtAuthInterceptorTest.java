package com.pqc.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pqc.gateway.client.CryptoEngineClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class JwtAuthInterceptorTest {

    private JwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtAuthInterceptor(
                mock(CryptoEngineClient.class),
                new JwtKeyCache(),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        interceptor.shutdown();
    }

    // 인수조건: Authorization 헤더 없음 → 401
    @Test
    void preHandle_missingBearer_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertFalse(result);
        assert resp.getStatus() == UNAUTHORIZED.value();
    }

    // 인수조건: verifiedCache 히트 → 200 (crypto-engine 미호출)
    @Test
    void preHandle_cacheHit_returns200() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;

        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"ML-DSA-65\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"test\",\"exp\":" + exp + ",\"jti\":\"test-jti\"}").getBytes(StandardCharsets.UTF_8));
        String token = headerB64 + "." + payloadB64 + ".fakesig";

        byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        String tokenHash = HexFormat.of().formatHex(hashBytes);
        interceptor.verifiedCache.put(tokenHash, exp);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertTrue(result);
    }
}
