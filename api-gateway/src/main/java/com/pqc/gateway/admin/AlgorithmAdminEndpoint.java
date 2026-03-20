package com.pqc.gateway.admin;

import com.pqc.gateway.service.AlgorithmHotSwapService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hot-swap Admin Endpoint (Day 10 — R1 해소)
 * Management 포트(8081)에서만 노출 — 외부 공개 포트(8080) 미노출.
 * GET  /actuator/algorithm      → 현재 알고리즘 ID 조회
 * POST /actuator/algorithm      → kemId / dsaId 런타임 전환 (null 파라미터 = 변경 없음)
 *
 * 보안 설계:
 *  - docker-compose ports에 8081 미게시 → 네트워크 격리로 외부 접근 차단
 *  - AlgorithmHotSwapService에서 허용 목록 검증 → 400 반환
 */
@Component
@Endpoint(id = "algorithm")
public class AlgorithmAdminEndpoint {

    private final AlgorithmHotSwapService hotSwapService;

    public AlgorithmAdminEndpoint(AlgorithmHotSwapService hotSwapService) {
        this.hotSwapService = hotSwapService;
    }

    @ReadOperation
    public Map<String, String> currentAlgorithms() {
        return Map.of(
                "kemId", hotSwapService.getKemId(),
                "dsaId", hotSwapService.getDsaId()
        );
    }

    @WriteOperation
    public Map<String, String> updateAlgorithms(@Nullable String kemId, @Nullable String dsaId) {
        if (kemId != null) {
            hotSwapService.setKemId(kemId);
        }
        if (dsaId != null) {
            hotSwapService.setDsaId(dsaId);
        }
        return Map.of(
                "kemId", hotSwapService.getKemId(),
                "dsaId", hotSwapService.getDsaId()
        );
    }
}
