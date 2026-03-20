package com.pqc.gateway.admin;

import com.pqc.gateway.config.CryptoAlgorithmProperties;
import com.pqc.gateway.service.AlgorithmHotSwapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlgorithmAdminEndpointTest {

    private AlgorithmHotSwapService hotSwapService;
    private AlgorithmAdminEndpoint endpoint;

    @BeforeEach
    void setUp() {
        CryptoAlgorithmProperties props = new CryptoAlgorithmProperties();
        props.setKemId("ML-KEM-768");
        props.setDsaId("ML-DSA-65");
        hotSwapService = new AlgorithmHotSwapService(props);
        endpoint = new AlgorithmAdminEndpoint(hotSwapService);
    }

    // TC1: GET → 초기값 반환
    @Test
    void readOperation_returnsCurrentAlgorithms() {
        Map<String, String> result = endpoint.currentAlgorithms();
        assertThat(result).containsEntry("kemId", "ML-KEM-768")
                          .containsEntry("dsaId", "ML-DSA-65");
    }

    // TC2: POST kemId만 → KEM 전환, DSA 유지
    @Test
    void writeOperation_kemOnly_updateKemKeepDsa() {
        Map<String, String> result = endpoint.updateAlgorithms("ML-KEM-512", null);
        assertThat(result).containsEntry("kemId", "ML-KEM-512")
                          .containsEntry("dsaId", "ML-DSA-65");
        assertThat(hotSwapService.getKemId()).isEqualTo("ML-KEM-512");
    }

    // TC3: POST dsaId만 → DSA 전환, KEM 유지
    @Test
    void writeOperation_dsaOnly_updateDsaKeepKem() {
        Map<String, String> result = endpoint.updateAlgorithms(null, "ML-DSA-87");
        assertThat(result).containsEntry("kemId", "ML-KEM-768")
                          .containsEntry("dsaId", "ML-DSA-87");
    }

    // TC4: POST 불허용 KEM → 400 예외, 기존 값 유지
    @Test
    void writeOperation_invalidKem_throws400AndKeepsOldValue() {
        assertThatThrownBy(() -> endpoint.updateAlgorithms("RSA-2048", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("RSA-2048");
        assertThat(hotSwapService.getKemId()).isEqualTo("ML-KEM-768");
    }

    // TC5: POST kemId + dsaId 동시 → 둘 다 전환
    @Test
    void writeOperation_bothParams_updatesBoth() {
        Map<String, String> result = endpoint.updateAlgorithms("ML-KEM-512", "ML-DSA-44");
        assertThat(result).containsEntry("kemId", "ML-KEM-512")
                          .containsEntry("dsaId", "ML-DSA-44");
    }
}
