package com.pqc.gateway.service;

import com.pqc.gateway.config.CryptoAlgorithmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlgorithmHotSwapServiceTest {

    private AlgorithmHotSwapService buildService(String kemId, String dsaId) {
        CryptoAlgorithmProperties props = new CryptoAlgorithmProperties();
        props.setKemId(kemId);
        props.setDsaId(dsaId);
        return new AlgorithmHotSwapService(props);
    }

    @Test
    void initialValues_matchProperties() {
        AlgorithmHotSwapService svc = buildService("ML-KEM-512", "ML-DSA-44");
        assertThat(svc.getKemId()).isEqualTo("ML-KEM-512");
        assertThat(svc.getDsaId()).isEqualTo("ML-DSA-44");
    }

    @Test
    void setKemId_allowedValue_updates() {
        AlgorithmHotSwapService svc = buildService("ML-KEM-768", "ML-DSA-65");
        svc.setKemId("ML-KEM-512");
        assertThat(svc.getKemId()).isEqualTo("ML-KEM-512");
    }

    @Test
    void setKemId_notAllowed_throws400() {
        AlgorithmHotSwapService svc = buildService("ML-KEM-768", "ML-DSA-65");
        assertThatThrownBy(() -> svc.setKemId("RSA-2048"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("RSA-2048");
    }

    @Test
    void setDsaId_allowedValue_updates() {
        AlgorithmHotSwapService svc = buildService("ML-KEM-768", "ML-DSA-65");
        svc.setDsaId("ML-DSA-87");
        assertThat(svc.getDsaId()).isEqualTo("ML-DSA-87");
    }

    @Test
    void setDsaId_notAllowed_throws400() {
        AlgorithmHotSwapService svc = buildService("ML-KEM-768", "ML-DSA-65");
        assertThatThrownBy(() -> svc.setDsaId("ECDSA-P256"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ECDSA-P256");
    }

    @Test
    void setKemId_allAllowedValues_succeed() {
        AlgorithmHotSwapService svc = buildService("ML-KEM-768", "ML-DSA-65");
        for (String allowed : AlgorithmHotSwapService.ALLOWED_KEM) {
            svc.setKemId(allowed);
            assertThat(svc.getKemId()).isEqualTo(allowed);
        }
    }
}
