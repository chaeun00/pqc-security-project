package com.pqc.gateway;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Day 18 모니터링 스택 통합 테스트 — docker-compose 기동 상태에서만 실행.
 * 실행 방법: ./gradlew test -Dmonitoring.integration=true
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "monitoring.integration", matches = "true")
class MonitoringIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void prometheus_rulesEndpoint_containsPqcAlerts() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9090/api/v1/rules"))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("PqcApiGatewayDown");
        assertThat(res.body()).contains("PqcHighRiskAlgorithmDetected");
    }

    @Test
    void grafana_datasourceHealthApi_returns200() throws Exception {
        String credentials = System.getenv("IT_ADMIN_CREDENTIALS");
        assumeTrue(credentials != null, "IT_ADMIN_CREDENTIALS not set — skipping Grafana auth test");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3001/api/health"))
                .header("Authorization", "Basic " + credentials)
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("ok");
    }
}
