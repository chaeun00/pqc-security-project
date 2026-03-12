package com.pqc.gateway.filter;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JWT 발급 시 공개키를 jti 기준으로 저장. 검증 필터에서 조회.
 * 60초 주기로 만료된 엔트리를 정리한다.
 */
@Component
public class JwtKeyCache {

    public record KeyEntry(String publicKey, long expiry) {}

    private final ConcurrentHashMap<String, KeyEntry> store = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public JwtKeyCache() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jwtkey-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanup, 60, 60, TimeUnit.SECONDS);
    }

    public void put(String jti, String publicKey, long expiry) {
        store.put(jti, new KeyEntry(publicKey, expiry));
    }

    public Optional<KeyEntry> get(String jti) {
        KeyEntry entry = store.get(jti);
        if (entry == null) return Optional.empty();
        if (Instant.now().getEpochSecond() > entry.expiry()) {
            store.remove(jti);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /** 만료된 jti 엔트리를 제거한다. */
    void cleanup() {
        long now = Instant.now().getEpochSecond();
        store.entrySet().removeIf(e -> now > e.getValue().expiry());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
