package com.pqc.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * CBOM 암호화 요청 카운터 — risk_level 태그별로 cbom_assets_total 발행 (Day 18)
 * PqcHighRiskAlgorithmDetected 알람 발동 기준 메트릭.
 */
@Component
public class CbomMetrics {

    private final MeterRegistry registry;

    public CbomMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordEncrypt(String riskLevel) {
        Counter.builder("cbom_assets_total")
                .tag("risk_level", riskLevel)
                .register(registry)
                .increment();
    }
}
