package com.pqc.gateway.service;

import com.pqc.gateway.config.RiskLevel;
import com.pqc.gateway.dto.EncryptRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 요청 risk_level 필드 → RiskLevel enum 분류 (Day 9/10)
 * null/blank → MEDIUM 폴백, 소문자 허용(정규화), 인식 불가 값 → 400
 */
@Service
public class RiskClassifier {

    public RiskLevel classify(EncryptRequest request) {
        if (request.riskLevel() == null || request.riskLevel().isBlank()) {
            return RiskLevel.MEDIUM;
        }
        try {
            return RiskLevel.valueOf(request.riskLevel().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "risk_level must be HIGH, MEDIUM, or LOW (got: '" + request.riskLevel() + "')");
        }
    }
}
