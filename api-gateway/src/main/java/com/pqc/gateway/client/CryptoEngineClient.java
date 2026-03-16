package com.pqc.gateway.client;

import com.pqc.gateway.dto.DsaSignRequest;
import com.pqc.gateway.dto.DsaSignResponse;
import com.pqc.gateway.dto.DsaVerifyRequest;
import com.pqc.gateway.dto.DsaVerifyResponse;
import com.pqc.gateway.dto.KemEncryptRequest;
import com.pqc.gateway.dto.KemEncryptResponse;
import com.pqc.gateway.dto.KemInitResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "crypto-engine", url = "${crypto.engine.url}", fallback = CryptoEngineClientFallback.class)
public interface CryptoEngineClient {

    @PostMapping("/dsa/sign")
    DsaSignResponse sign(@RequestBody DsaSignRequest request);

    @PostMapping("/dsa/verify")
    DsaVerifyResponse verify(@RequestBody DsaVerifyRequest request);

    @PostMapping("/kem/init")
    KemInitResponse kemInit();

    @PostMapping("/kem/encrypt")
    KemEncryptResponse kemEncrypt(@RequestBody KemEncryptRequest request);
}
