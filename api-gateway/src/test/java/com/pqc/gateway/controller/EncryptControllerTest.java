package com.pqc.gateway.controller;

import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.dto.DsaVerifyResponse;
import com.pqc.gateway.dto.KemEncryptResponse;
import com.pqc.gateway.dto.KemInitResponse;
import com.pqc.gateway.filter.JwtKeyCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EncryptController.class)
class EncryptControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CryptoEngineClient cryptoEngineClient;

    @MockBean
    JwtKeyCache jwtKeyCache;

    private String validToken;

    @BeforeEach
    void setUp() {
        // 유효기간이 남은 JWT 페이로드를 base64url 인코딩하여 가짜 토큰 구성
        long futureExp = Instant.now().getEpochSecond() + 3600;
        String payloadJson = String.format(
                "{\"sub\":\"demo\",\"exp\":%d,\"jti\":\"test-jti\"}", futureExp);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        validToken = "eyJhbGciOiJNTC1EU0EtNjUifQ." + encodedPayload + ".fakesig";

        // JwtKeyCache: test-jti 조회 시 공개키 반환
        when(jwtKeyCache.get("test-jti"))
                .thenReturn(Optional.of(new JwtKeyCache.KeyEntry("fake-public-key", futureExp)));
        // crypto-engine DSA 서명 검증: 항상 valid=true 반환
        when(cryptoEngineClient.verify(any()))
                .thenReturn(new DsaVerifyResponse("ML-DSA-65", true));
    }

    // 인수조건 1: 유효 JWT + 정상 요청 → 200 + {key_id, kem_ciphertext, aes_ciphertext, aes_iv}
    @Test
    void encrypt_success_returns200WithAllFields() throws Exception {
        when(cryptoEngineClient.kemInit())
                .thenReturn(new KemInitResponse(1L, "ML-KEM-768"));
        when(cryptoEngineClient.kemEncrypt(any()))
                .thenReturn(new KemEncryptResponse("ML-KEM-768", "kem_ct_val", "aes_ct_val", "aes_iv_val"));

        mvc.perform(post("/api/encrypt")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plaintext\":\"aGVsbG8=\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key_id", is(1)))
                .andExpect(jsonPath("$.algorithm", is("ML-KEM-768")))
                .andExpect(jsonPath("$.kem_ciphertext", is("kem_ct_val")))
                .andExpect(jsonPath("$.aes_ciphertext", is("aes_ct_val")))
                .andExpect(jsonPath("$.aes_iv", is("aes_iv_val")));
    }

    // 인수조건 2: JWT 없음 → 401
    @Test
    void encrypt_noToken_returns401() throws Exception {
        mvc.perform(post("/api/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plaintext\":\"aGVsbG8=\"}"))
                .andExpect(status().isUnauthorized());
    }

    // 인수조건 3: plaintext 누락 → 400
    @Test
    void encrypt_missingPlaintext_returns400() throws Exception {
        mvc.perform(post("/api/encrypt")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // 인수조건 4: crypto-engine 다운 (kemInit fallback) → 503
    @Test
    void encrypt_cryptoEngineDown_returns503() throws Exception {
        when(cryptoEngineClient.kemInit())
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable"));

        mvc.perform(post("/api/encrypt")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plaintext\":\"aGVsbG8=\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
