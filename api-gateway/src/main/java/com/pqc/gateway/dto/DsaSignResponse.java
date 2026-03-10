package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DsaSignResponse(
        String algorithm,
        String message,
        String signature,
        @JsonProperty("public_key") String publicKey) {}
