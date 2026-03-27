package com.pqc.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CbomMetricsTest {

    // Day 18: cbom_assets_total counter가 risk_level 태그별로 정확히 증가하는지 검증
    @Test
    void recordEncrypt_incrementsCounterByRiskLevel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CbomMetrics metrics = new CbomMetrics(registry);

        metrics.recordEncrypt("HIGH");
        metrics.recordEncrypt("HIGH");
        metrics.recordEncrypt("MEDIUM");

        Counter highCounter = registry.find("cbom_assets_total")
                .tag("risk_level", "HIGH")
                .counter();
        Counter mediumCounter = registry.find("cbom_assets_total")
                .tag("risk_level", "MEDIUM")
                .counter();

        assertThat(highCounter).isNotNull();
        assertThat(highCounter.count()).isEqualTo(2.0);
        assertThat(mediumCounter).isNotNull();
        assertThat(mediumCounter.count()).isEqualTo(1.0);
    }
}
