package com.pqc.gateway.controller;

import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.dto.DsaSignRequest;
import com.pqc.gateway.dto.DsaSignResponse;
import com.pqc.gateway.dto.DsaVerifyRequest;
import com.pqc.gateway.dto.DsaVerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dsa")
public class DsaController {

    private final CryptoEngineClient cryptoEngineClient;

    public DsaController(CryptoEngineClient cryptoEngineClient) {
        this.cryptoEngineClient = cryptoEngineClient;
    }

    @PostMapping("/sign")
    public ResponseEntity<DsaSignResponse> sign(@Valid @RequestBody DsaSignRequest request) {
        return ResponseEntity.ok(cryptoEngineClient.sign(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<DsaVerifyResponse> verify(@Valid @RequestBody DsaVerifyRequest request) {
        return ResponseEntity.ok(cryptoEngineClient.verify(request));
    }
}
