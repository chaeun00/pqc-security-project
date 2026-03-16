package com.pqc.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

public record KemEncryptRequest(
        @Positive @JsonProperty("key_id") long keyId) {}
