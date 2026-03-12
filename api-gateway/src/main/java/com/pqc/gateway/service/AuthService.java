package com.pqc.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.dto.DsaSignRequest;
import com.pqc.gateway.dto.DsaSignResponse;
import com.pqc.gateway.dto.LoginRequest;
import com.pqc.gateway.dto.LoginResponse;
import com.pqc.gateway.filter.JwtKeyCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ALGORITHM = "ML-DSA-65";

    @Value("${auth.demo.user}")
    private String demoUser;

    @Value("${auth.demo.password}")
    private String demoPassword;

    private final CryptoEngineClient cryptoEngineClient;
    private final ObjectMapper objectMapper;
    private final JwtKeyCache jwtKeyCache;

    public AuthService(CryptoEngineClient cryptoEngineClient, ObjectMapper objectMapper, JwtKeyCache jwtKeyCache) {
        this.cryptoEngineClient = cryptoEngineClient;
        this.objectMapper = objectMapper;
        this.jwtKeyCache = jwtKeyCache;
    }

    public LoginResponse login(LoginRequest request) {
        // constant-time 비교 — 타이밍 공격 방지
        boolean userMatch = MessageDigest.isEqual(
                request.userId().getBytes(StandardCharsets.UTF_8),
                demoUser.getBytes(StandardCharsets.UTF_8));
        boolean passMatch = MessageDigest.isEqual(
                request.password().getBytes(StandardCharsets.UTF_8),
                demoPassword.getBytes(StandardCharsets.UTF_8));
        if (!userMatch || !passMatch) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        try {
            String headerB64 = encodeJson(Map.of("alg", ALGORITHM, "typ", "JWT"));
            long now = Instant.now().getEpochSecond();
            long exp = now + 3600;
            String jti = UUID.randomUUID().toString();
            String payloadB64 = encodeJson(Map.of(
                    "sub", request.userId(),
                    "alg", ALGORITHM,
                    "iat", now,
                    "exp", exp,
                    "jti", jti));

            String signingInput = headerB64 + "." + payloadB64;

            long start = System.nanoTime();
            DsaSignResponse signResponse = cryptoEngineClient.sign(new DsaSignRequest(signingInput));
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[AUTH-LATENCY] crypto-engine /dsa/sign latency={}ms", latencyMs);

            // 공개키 서버 측 캐시 저장 — 검증 필터에서 사용
            jwtKeyCache.put(jti, signResponse.publicKey(), exp);

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
