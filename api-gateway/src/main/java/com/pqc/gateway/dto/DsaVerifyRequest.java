package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DsaVerifyRequest(
        @NotBlank @Size(max = 65536) String message,
        @NotBlank String signature,
        @NotBlank @JsonProperty("public_key") String publicKey) {}
