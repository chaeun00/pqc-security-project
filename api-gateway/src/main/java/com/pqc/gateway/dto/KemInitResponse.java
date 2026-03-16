package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KemInitResponse(
        @JsonProperty("key_id") long keyId,
        String algorithm) {}
