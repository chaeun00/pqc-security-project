package com.pqc.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EncryptRequest(
        @NotBlank @Size(max = 65536) String plaintext) {}
