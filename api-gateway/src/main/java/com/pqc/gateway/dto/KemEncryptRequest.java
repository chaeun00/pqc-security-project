package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record KemEncryptRequest(
        @Positive @JsonProperty("key_id") long keyId,
        @NotBlank @Size(max = 65536) String plaintext) {}
