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
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Feign FallbackFactory — 4xx는 원래 상태코드로 전파, 5xx/네트워크 오류는 503.
 * 단순 fallback 클래스 대신 FallbackFactory를 사용해 원인 예외를 검사한다.
 */
@Component
public class CryptoEngineClientFallbackFactory implements FallbackFactory<CryptoEngineClient> {

    @Override
    public CryptoEngineClient create(Throwable cause) {
        return new CryptoEngineClient() {

            /** ExecutionException 언래핑 후 4xx → 원래 상태코드 전파, 그 외 → 503 */
            private RuntimeException resolve() {
                // Spring Cloud CB가 FeignException을 ExecutionException으로 감싸는 경우 대응
                Throwable actual = (cause instanceof java.util.concurrent.ExecutionException)
                        ? cause.getCause() : cause;
                if (actual instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
                    HttpStatus status = HttpStatus.resolve(fe.status());
                    return new ResponseStatusException(
                            status != null ? status : HttpStatus.BAD_REQUEST,
                            "crypto-engine: " + fe.status());
                }
                return new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable");
            }

            @Override
            public DsaSignResponse sign(DsaSignRequest request) {
                throw resolve();
            }

            @Override
            public DsaVerifyResponse verify(DsaVerifyRequest request) {
                throw resolve();
            }

            @Override
            public KemInitResponse kemInit() {
                throw resolve();
            }

            @Override
            public KemEncryptResponse kemEncrypt(KemEncryptRequest request) {
                throw resolve();
            }

            @Override
            public KemDecryptResponse kemDecrypt(KemDecryptRequest request) {
                throw resolve();
            }
        };
    }
}
