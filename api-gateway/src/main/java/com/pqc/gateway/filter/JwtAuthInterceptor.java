package com.pqc.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.dto.DsaVerifyRequest;
import com.pqc.gateway.dto.DsaVerifyResponse;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JWT 검증 인터셉터 — 3단계 (exp → 서명검증캐시 → crypto-engine)
 * /api/auth/** 는 WebMvcConfig 에서 제외.
 */
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    // SHA-256(token) → expiry (epoch second)
    private final ConcurrentHashMap<String, Long> verifiedCache = new ConcurrentHashMap<>();

    /** 테스트 전용: verifiedCache에 항목을 직접 주입한다. */
    void putVerifiedCache(String tokenHash, long expiry) {
        verifiedCache.put(tokenHash, expiry);
    }

    private final CryptoEngineClient cryptoEngineClient;
    private final JwtKeyCache jwtKeyCache;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    public JwtAuthInterceptor(CryptoEngineClient cryptoEngineClient, JwtKeyCache jwtKeyCache,
                              ObjectMapper objectMapper) {
        this.cryptoEngineClient = cryptoEngineClient;
        this.jwtKeyCache = jwtKeyCache;
        this.objectMapper = objectMapper;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jwt-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanup, 60, 60, TimeUnit.SECONDS);
    }

    /** 만료된 verifiedCache 엔트리를 제거한다. */
    void cleanup() {
        long now = Instant.now().getEpochSecond();
        verifiedCache.entrySet().removeIf(e -> now > e.getValue());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return false;
        }

        String token = authHeader.substring(7);
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return false;
        }

        // 1단계: exp 확인
        JsonNode payload;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            payload = objectMapper.readTree(payloadBytes);
        } catch (Exception e) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return false;
        }

        long exp = payload.path("exp").asLong(0);
        if (exp == 0 || Instant.now().getEpochSecond() > exp) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return false;
        }

        // 2단계: 서명 검증 캐시 확인
        String tokenHash = sha256Hex(token);
        Long cachedExpiry = verifiedCache.get(tokenHash);
        if (cachedExpiry != null && Instant.now().getEpochSecond() <= cachedExpiry) {
            return true;
        }

        // 3단계: crypto-engine 서명 검증
        String jti = payload.path("jti").asText(null);
        if (jti == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return false;
        }

        var keyEntry = jwtKeyCache.get(jti);
        if (keyEntry.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return false;
        }

        String message = parts[0] + "." + parts[1];
        try {
            DsaVerifyResponse verifyResp = cryptoEngineClient.verify(
                    new DsaVerifyRequest(message, parts[2], keyEntry.get().publicKey()));
            if (!verifyResp.valid()) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
                return false;
            }
        } catch (Exception e) {
            log.error("[JWT-VERIFY] crypto-engine 호출 실패", e);
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
            return false;
        }

        // 검증 성공 → 캐시 저장
        verifiedCache.put(tokenHash, exp);

        return true;
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
