package com.pqc.gateway.client;

import com.pqc.gateway.dto.DsaSignRequest;
import com.pqc.gateway.dto.DsaSignResponse;
import com.pqc.gateway.dto.DsaVerifyRequest;
import com.pqc.gateway.dto.DsaVerifyResponse;
import com.pqc.gateway.dto.KemDecryptRequest;
import com.pqc.gateway.dto.KemDecryptResponse;
import com.pqc.gateway.dto.KemEncryptRequest;
import com.pqc.gateway.dto.KemEncryptResponse;
import com.pqc.gateway.dto.KemInitResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CryptoEngineClientFallback implements CryptoEngineClient {

    @Override
    public DsaSignResponse sign(DsaSignRequest request) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable");
    }

    @Override
    public DsaVerifyResponse verify(DsaVerifyRequest request) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable");
    }

    @Override
    public KemInitResponse kemInit(String kemAlgorithmId) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable");
    }

    @Override
    public KemEncryptResponse kemEncrypt(String kemAlgorithmId, String riskLevel, KemEncryptRequest request) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable");
    }

    @Override
    public KemDecryptResponse kemDecrypt(KemDecryptRequest request) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable");
    }
}
