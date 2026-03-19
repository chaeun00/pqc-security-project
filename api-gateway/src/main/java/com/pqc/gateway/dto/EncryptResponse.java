package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EncryptResponse(
        @JsonProperty("key_id") long keyId,
        String algorithm,
        @JsonProperty("kem_ciphertext") String kemCiphertext,
        @JsonProperty("aes_ciphertext") String aesCiphertext,
        @JsonProperty("aes_iv") String aesIv,
        @JsonProperty("risk_level") String riskLevel) {}
