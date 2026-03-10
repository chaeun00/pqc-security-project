package com.pqc.gateway.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "crypto.engine")
@Validated
public class CryptoEngineProperties {

    @NotBlank
    private String url;

    /**
     * HTTP(평문) 연결 허용 여부.
     * 기본값 false → HTTPS 강제 (프로덕션 보안 요건).
     * 개발/테스트 환경에서는 application-dev.yml 또는 테스트 프로퍼티로 true 설정.
     */
    private boolean allowHttp = false;

    @AssertTrue(message = "crypto.engine.url must use HTTPS. Set crypto.engine.allow-http=true for dev/test.")
    public boolean isUrlSecure() {
        return allowHttp || url == null || url.startsWith("https://");
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public boolean isAllowHttp() { return allowHttp; }
    public void setAllowHttp(boolean allowHttp) { this.allowHttp = allowHttp; }
}
