package com.pqc.gateway.service;

import com.pqc.gateway.config.RiskLevel;
import com.pqc.gateway.dto.EncryptRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskClassifierTest {

    private final RiskClassifier classifier = new RiskClassifier();

    @Test
    void classify_nullRiskLevel_returnsMedium() {
        assertThat(classifier.classify(new EncryptRequest("plain", null)))
                .isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void classify_blankRiskLevel_returnsMedium() {
        assertThat(classifier.classify(new EncryptRequest("plain", "   ")))
                .isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void classify_lowercaseHigh_returnsHigh() {
        assertThat(classifier.classify(new EncryptRequest("plain", "high")))
                .isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void classify_HIGH_returnsHigh() {
        assertThat(classifier.classify(new EncryptRequest("plain", "HIGH")))
                .isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void classify_MEDIUM_returnsMedium() {
        assertThat(classifier.classify(new EncryptRequest("plain", "MEDIUM")))
                .isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void classify_LOW_returnsLow() {
        assertThat(classifier.classify(new EncryptRequest("plain", "LOW")))
                .isEqualTo(RiskLevel.LOW);
    }

    @Test
    void classify_invalidValue_throws400() {
        assertThatThrownBy(() -> classifier.classify(new EncryptRequest("plain", "SUPER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void classify_numericValue_throws400() {
        assertThatThrownBy(() -> classifier.classify(new EncryptRequest("plain", "5")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
