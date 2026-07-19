package com.clicker.mousehub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clicker.mousehub.service.EmailVerificationService;
import com.clicker.mousehub.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiIntegrationTest.MailTestConfig.class)
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RecordingMailService mail;

    @Test void publicCatalogAndOptionsAreAvailable() throws Exception {
        mvc.perform(get("/api/v1/mice"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page.number", is(1)));
        mvc.perform(get("/api/v1/review-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gripStyles", hasSize(4)))
                .andExpect(jsonPath("$.proTags", hasSize(9)));
    }

    @Test void realtimeStreamIsPublicAndProtectedFromProxyBuffering() throws Exception {
        mvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    @Test void registrationReturnsJwtAndMeRequiresToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register/code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"USER@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds", is(600)))
                .andExpect(jsonPath("$.resendAfterSeconds", is(60)));
        String registrationCode = mail.codeFor(EmailVerificationService.REGISTER);

        String body = "{\"email\":\"USER@example.com\",\"password\":\"password123\",\"verificationCode\":\""
                + registrationCode + "\"}";
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.email", is("user@example.com")))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("USER")));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/password/code").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String passwordCode = mail.codeFor(EmailVerificationService.CHANGE_PASSWORD);

        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationCode\":\"000000\",\"newPassword\":\"new-password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_VERIFICATION_CODE")));
        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationCode\":\"" + passwordCode
                                + "\",\"newPassword\":\"new-password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("密码修改成功")));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"new-password123\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationCode\":\"" + passwordCode
                                + "\",\"newPassword\":\"another-password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_VERIFICATION_CODE")));
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean
        @Primary
        RecordingMailService recordingMailService() { return new RecordingMailService(); }
    }

    static class RecordingMailService extends MailService {
        private final java.util.Map<String, String> sentCodes = new java.util.concurrent.ConcurrentHashMap<>();

        RecordingMailService() { super(null, false, ""); }

        @Override
        public void verificationCode(String recipient, String code, long expiresMinutes, String purpose) {
            sentCodes.put(purpose, code);
        }

        @Override
        public void welcome(String recipient) {}

        String codeFor(String purpose) { return sentCodes.get(purpose); }
    }
}
