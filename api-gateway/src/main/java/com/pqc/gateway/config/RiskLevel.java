package com.pqc.gateway.config;

/**
 * SNDL 위협 기반 민감도 레벨 — ML-KEM 알고리즘 자동 매핑 (Day 9)
 * HIGH → ML-KEM-1024, MEDIUM → ML-KEM-768, LOW → ML-KEM-512
 */
public enum RiskLevel {
    HIGH("ML-KEM-1024"),
    MEDIUM("ML-KEM-768"),
    LOW("ML-KEM-512");

    private final String kemAlgorithm;

    RiskLevel(String kemAlgorithm) {
        this.kemAlgorithm = kemAlgorithm;
    }

    public String toKemAlgorithm() {
        return kemAlgorithm;
    }
}
