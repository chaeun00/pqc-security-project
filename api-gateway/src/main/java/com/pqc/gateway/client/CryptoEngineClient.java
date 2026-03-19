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
import com.pqc.gateway.config.CryptoEngineClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "crypto-engine",
    url = "${crypto.engine.url}",
    fallbackFactory = CryptoEngineClientFallbackFactory.class,
    configuration = CryptoEngineClientConfig.class
)
public interface CryptoEngineClient {

    @PostMapping("/dsa/sign")
    DsaSignResponse sign(@RequestBody DsaSignRequest request);

    @PostMapping("/dsa/verify")
    DsaVerifyResponse verify(@RequestBody DsaVerifyRequest request);

    @PostMapping("/kem/init")
    KemInitResponse kemInit(@RequestHeader("X-Kem-Algorithm-Id") String kemAlgorithmId);

    @PostMapping("/kem/encrypt")
    KemEncryptResponse kemEncrypt(@RequestHeader("X-Kem-Algorithm-Id") String kemAlgorithmId, @RequestHeader("X-Risk-Level") String riskLevel, @RequestBody KemEncryptRequest request);

    @PostMapping("/kem/decrypt")
    KemDecryptResponse kemDecrypt(@RequestBody KemDecryptRequest request);
}
