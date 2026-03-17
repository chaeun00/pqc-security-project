package com.pqc.gateway.controller;

import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.dto.EncryptRequest;
import com.pqc.gateway.dto.EncryptResponse;
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
@RequestMapping("/api/encrypt")
public class EncryptController {

    private final CryptoEngineClient cryptoEngineClient;

    public EncryptController(CryptoEngineClient cryptoEngineClient) {
        this.cryptoEngineClient = cryptoEngineClient;
    }

    @PostMapping
    public ResponseEntity<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
        KemInitResponse init = cryptoEngineClient.kemInit();
        KemEncryptResponse enc = cryptoEngineClient.kemEncrypt(
                new KemEncryptRequest(init.keyId(), request.plaintext()));
        return ResponseEntity.ok(new EncryptResponse(
                init.keyId(),
                enc.algorithm(),
                enc.kemCiphertext(),
                enc.aesCiphertext(),
                enc.aesIv()));
    }
}
