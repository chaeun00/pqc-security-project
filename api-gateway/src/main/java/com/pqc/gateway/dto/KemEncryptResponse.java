package com.pqc.gateway.dto;

public record KemEncryptResponse(
        String algorithm,
        String ciphertext) {}
