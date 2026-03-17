package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record KemDecryptRequest(
        @Positive @JsonProperty("key_id") long keyId,
        @NotBlank @JsonProperty("kem_ciphertext") String kemCiphertext,
        @NotBlank @JsonProperty("aes_ciphertext") String aesCiphertext,
        @NotBlank @JsonProperty("aes_iv") String aesIv) {}
