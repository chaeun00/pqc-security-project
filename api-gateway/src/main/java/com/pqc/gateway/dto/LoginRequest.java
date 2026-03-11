package com.pqc.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 64) String userId,
        @NotBlank @Size(max = 128) String password) {}
