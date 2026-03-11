package com.pqc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.dto.DsaSignRequest;
import com.pqc.gateway.dto.DsaSignResponse;
import com.pqc.gateway.dto.LoginRequest;
import com.pqc.gateway.dto.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // 데모용 고정 인증 정보
    private static final String DEMO_USER = "demo";
    private static final String DEMO_PASSWORD = "demo123";

    private static final String ALGORITHM = "ML-DSA-65";

    private final CryptoEngineClient cryptoEngineClient;
    private final ObjectMapper objectMapper;

    public AuthService(CryptoEngineClient cryptoEngineClient, ObjectMapper objectMapper) {
        this.cryptoEngineClient = cryptoEngineClient;
        this.objectMapper = objectMapper;
    }

    public LoginResponse login(LoginRequest request) {
        if (!DEMO_USER.equals(request.userId()) || !DEMO_PASSWORD.equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        try {
            String headerB64 = encodeJson(Map.of("alg", ALGORITHM, "typ", "JWT"));
            long now = Instant.now().getEpochSecond();
            String payloadB64 = encodeJson(Map.of(
                    "sub", request.userId(),
                    "alg", ALGORITHM,
                    "iat", now,
                    "exp", now + 3600));

            String signingInput = headerB64 + "." + payloadB64;

            long start = System.nanoTime();
            DsaSignResponse signResponse = cryptoEngineClient.sign(new DsaSignRequest(signingInput));
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[AUTH-LATENCY] crypto-engine /dsa/sign latency={}ms", latencyMs);

            String token = headerB64 + "." + payloadB64 + "." + signResponse.signature();
            return new LoginResponse(token);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("JWT 생성 실패");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JWT 생성 실패");
        }
    }

    private String encodeJson(Map<String, Object> map) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(map);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }
}
