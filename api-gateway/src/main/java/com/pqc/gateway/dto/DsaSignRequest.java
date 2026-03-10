package com.pqc.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DsaSignRequest(
        @NotBlank @Size(max = 65536) String message) {}
