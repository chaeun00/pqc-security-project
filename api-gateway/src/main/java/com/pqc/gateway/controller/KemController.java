package com.pqc.gateway.controller;

import com.pqc.gateway.client.CryptoEngineClient;
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

    public KemController(CryptoEngineClient cryptoEngineClient) {
        this.cryptoEngineClient = cryptoEngineClient;
    }

    @PostMapping("/init")
    public ResponseEntity<KemInitResponse> init() {
        return ResponseEntity.ok(cryptoEngineClient.kemInit());
    }

    @PostMapping("/encrypt")
    public ResponseEntity<KemEncryptResponse> encrypt(@Valid @RequestBody KemEncryptRequest request) {
        return ResponseEntity.ok(cryptoEngineClient.kemEncrypt(request));
    }
}
