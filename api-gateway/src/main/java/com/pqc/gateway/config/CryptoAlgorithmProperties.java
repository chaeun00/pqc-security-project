package com.pqc.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Algorithm Agility — crypto.algorithm.* 프로퍼티 바인딩 (Day 8 Step 1)
 * application.yml: crypto.algorithm.kem-id / dsa-id
 * 환경변수: KEM_ALGORITHM_ID / DSA_ALGORITHM_ID (docker-compose 주입)
 */
@ConfigurationProperties(prefix = "crypto.algorithm")
public class CryptoAlgorithmProperties {

    private String kemId = "ML-KEM-768";
    private String dsaId = "ML-DSA-65";

    public String getKemId() { return kemId; }
    public void setKemId(String kemId) { this.kemId = kemId; }
    public String getDsaId() { return dsaId; }
    public void setDsaId(String dsaId) { this.dsaId = dsaId; }
}
