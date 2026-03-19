package com.pqc.gateway.controller;

import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.config.CryptoAlgorithmProperties;
import com.pqc.gateway.dto.KemDecryptRequest;
import com.pqc.gateway.dto.KemDecryptResponse;
import com.pqc.gateway.dto.KemEncryptRequest;
import com.pqc.gateway.dto.KemEncryptResponse;
import com.pqc.gateway.dto.KemInitResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kem")
public class KemController {

    private final CryptoEngineClient cryptoEngineClient;
    private final CryptoAlgorithmProperties algorithmProperties;

    public KemController(CryptoEngineClient cryptoEngineClient, CryptoAlgorithmProperties algorithmProperties) {
        this.cryptoEngineClient = cryptoEngineClient;
        this.algorithmProperties = algorithmProperties;
    }

    @PostMapping("/init")
    public ResponseEntity<KemInitResponse> init() {
        return ResponseEntity.ok(cryptoEngineClient.kemInit(algorithmProperties.getKemId()));
    }

    @PostMapping("/encrypt")
    public ResponseEntity<KemEncryptResponse> encrypt(@Valid @RequestBody KemEncryptRequest request) {
        return ResponseEntity.ok(cryptoEngineClient.kemEncrypt(algorithmProperties.getKemId(), "NONE", request));
    }

    @PostMapping("/decrypt")
    public ResponseEntity<KemDecryptResponse> decrypt(@Valid @RequestBody KemDecryptRequest request) {
        return ResponseEntity.ok(cryptoEngineClient.kemDecrypt(request));
    }
}
