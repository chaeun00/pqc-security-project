package com.pqc.gateway.service;

import com.pqc.gateway.config.CryptoAlgorithmProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime Algorithm Hot-swap (Day 10 — R1 해소)
 * AtomicReference로 KEM/DSA 알고리즘 ID를 재기동 없이 전환한다.
 * 초기값: CryptoAlgorithmProperties (환경변수 KEM_ALGORITHM_ID / DSA_ALGORITHM_ID).
 * 전환 후 신규 요청부터 즉시 적용 — 진행 중인 요청은 기존 알고리즘 유지.
 * 보안: 허용 목록(whitelist) 외 값은 400으로 거부.
 */
@Service
public class AlgorithmHotSwapService {

    static final Set<String> ALLOWED_KEM = Set.of("ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
    static final Set<String> ALLOWED_DSA = Set.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

    private final AtomicReference<String> kemId;
    private final AtomicReference<String> dsaId;

    public AlgorithmHotSwapService(CryptoAlgorithmProperties props) {
        this.kemId = new AtomicReference<>(props.getKemId());
        this.dsaId = new AtomicReference<>(props.getDsaId());
    }

    public String getKemId() { return kemId.get(); }
    public String getDsaId() { return dsaId.get(); }

    /** KEM 알고리즘 전환 — 허용 목록 외 값은 400. */
    public void setKemId(String newKemId) {
        if (!ALLOWED_KEM.contains(newKemId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "허용되지 않는 KEM 알고리즘: " + newKemId + ". 허용: " + ALLOWED_KEM);
        }
        kemId.set(newKemId);
    }

    /** DSA 알고리즘 전환 — 허용 목록 외 값은 400. */
    public void setDsaId(String newDsaId) {
        if (!ALLOWED_DSA.contains(newDsaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "허용되지 않는 DSA 알고리즘: " + newDsaId + ". 허용: " + ALLOWED_DSA);
        }
        dsaId.set(newDsaId);
    }
}
