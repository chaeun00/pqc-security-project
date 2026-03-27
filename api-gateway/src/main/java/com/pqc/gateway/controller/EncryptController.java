package com.pqc.gateway.controller;

import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.config.RiskLevel;
import com.pqc.gateway.dto.EncryptRequest;
import com.pqc.gateway.dto.EncryptResponse;
import com.pqc.gateway.dto.KemEncryptRequest;
import com.pqc.gateway.dto.KemEncryptResponse;
import com.pqc.gateway.dto.KemInitResponse;
import com.pqc.gateway.service.CbomMetrics;
import com.pqc.gateway.service.RiskClassifier;
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
    private final RiskClassifier riskClassifier;
    private final CbomMetrics cbomMetrics;

    public EncryptController(CryptoEngineClient cryptoEngineClient, RiskClassifier riskClassifier,
                             CbomMetrics cbomMetrics) {
        this.cryptoEngineClient = cryptoEngineClient;
        this.riskClassifier = riskClassifier;
        this.cbomMetrics = cbomMetrics;
    }

    @PostMapping
    public ResponseEntity<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
        RiskLevel risk = riskClassifier.classify(request);
        cbomMetrics.recordEncrypt(risk.name());
        String kemAlgorithm = riskClassifier.resolveAlgorithm(risk);
        KemInitResponse init = cryptoEngineClient.kemInit(kemAlgorithm);
        KemEncryptResponse enc = cryptoEngineClient.kemEncrypt(kemAlgorithm, risk.name(),
                new KemEncryptRequest(init.keyId(), request.plaintext()));
        return ResponseEntity.ok(new EncryptResponse(
                init.keyId(),
                enc.algorithm(),
                enc.kemCiphertext(),
                enc.aesCiphertext(),
                enc.aesIv(),
                risk.name()));
    }
}
