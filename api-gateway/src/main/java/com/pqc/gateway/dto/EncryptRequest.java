package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EncryptRequest(
        @NotBlank @Size(max = 65536) String plaintext,
        @JsonProperty("risk_level") String riskLevel) {}
