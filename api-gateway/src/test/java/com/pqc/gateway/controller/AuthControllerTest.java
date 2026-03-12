package com.pqc.gateway.controller;

import com.pqc.gateway.client.CryptoEngineClient;
import com.pqc.gateway.dto.DsaSignResponse;
import com.pqc.gateway.filter.JwtKeyCache;
import com.pqc.gateway.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(AuthService.class)
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CryptoEngineClient cryptoEngineClient;

    @MockBean
    JwtKeyCache jwtKeyCache;

    // 인수조건 1: 정상 로그인 → 200 + JWT 구조("." 2개)
    @Test
    void login_success_returns200WithToken() throws Exception {
        when(cryptoEngineClient.sign(any()))
                .thenReturn(new DsaSignResponse("ML-DSA-65", "input", "sig123", "pub456"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"demo","password":"testpass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.token").value(matchesPattern("[^.]+\\.[^.]+\\.[^.]+")));
    }

    // 인수조건 1: 잘못된 비밀번호 → 401
    @Test
    void login_invalidPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"demo","password":"wrongpass"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // 인수조건 2: crypto-engine 다운 (CB Fallback) → 503
    @Test
    void login_cryptoEngineDown_returns503() throws Exception {
        when(cryptoEngineClient.sign(any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "crypto-engine unavailable"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"demo","password":"testpass"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}
