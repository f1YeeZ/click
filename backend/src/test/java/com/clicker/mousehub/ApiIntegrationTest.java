package com.clicker.mousehub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.UserMapper;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.UUID;

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
    @Autowired UserMapper users;
    @Autowired MouseMapper mice;

    @Test void publicCatalogAndOptionsAreAvailable() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
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
        mvc.perform(get("/api/v1/mouse-rankings").param("dimension", "overall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/v1/mouse-rankings").param("dimension", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_RANKING_DIMENSION")));
        mvc.perform(get("/api/v1/mouse-recommendations")
                        .param("gripStyle", "CLAW").param("supportPositions", "PALM_CENTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/v1/mouse-comparisons")
                        .param("mouseIds", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test void mouseDetailUsesUuidAndMalformedLegacySlugIsRejected() throws Exception {
        mvc.perform(get("/api/v1/mice/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("MOUSE_NOT_FOUND")));
        mvc.perform(get("/api/v1/mice/logitech-g-pro-x-superlight-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_ARGUMENT")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "review-contract@example.com")
    void missingCurrentReviewReturnsNotFoundInsteadOfNull() throws Exception {
        users.insert(user("review-contract@example.com", "USER"));
        MouseDevice mouse = publishedMouse();
        mice.insert(mouse);
        mvc.perform(get("/api/v1/mice/" + mouse.getId() + "/reviews/mine"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("REVIEW_NOT_FOUND")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCreationAndStatusChangeUseResourceSemantics() throws Exception {
        String payload = """
                {
                  "brand":"Contract", "model":"REST Mouse", "variant":"",
                  "slug":"contract-rest-mouse", "sizeCategory":"MEDIUM",
                  "lengthMm":120, "widthMm":62, "heightMm":39, "weightG":58,
                  "shapeType":"SYMMETRICAL", "sensorName":"Test Sensor",
                  "maxDpi":26000, "maxPollingRateHz":1000,
                  "connectionModes":["wired"],
                  "primarySourceUrl":"https://example.com/mice/contract-rest-mouse"
                }
                """;
        String response = mvc.perform(post("/api/v1/admin/mice")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/mice/")))
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();

        mvc.perform(patch("/api/v1/admin/mice/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ARCHIVED")));
        mvc.perform(delete("/api/v1/admin/mice/" + id))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code", is("METHOD_NOT_ALLOWED")));
    }

    @Test void realtimeStreamIsPublicAndProtectedFromProxyBuffering() throws Exception {
        mvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    @Test void registrationReturnsJwtAndMeRequiresToken() throws Exception {
        mvc.perform(post("/api/v1/registration-verification-codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"USER@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/registration-verification-codes/current"))
                .andExpect(jsonPath("$.expiresInSeconds", is(600)))
                .andExpect(jsonPath("$.resendAfterSeconds", is(60)));
        String registrationCode = mail.codeFor(EmailVerificationService.REGISTER);

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"USER@example.com\",\"password\":\"password123\",\"verificationCode\":\""
                                + registrationCode + "\"}"))
                .andExpect(status().isBadRequest());

        String body = "{\"email\":\"USER@example.com\",\"password\":\"password123\",\"verificationCode\":\""
                + registrationCode + "\",\"acceptedTerms\":true}";
        String response = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/users/")))
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.email", is("user@example.com")))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("USER")));
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/password-verification-codes").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/password-verification-codes/current"));
        String passwordCode = mail.codeFor(EmailVerificationService.CHANGE_PASSWORD);

        mvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationCode\":\"000000\",\"newPassword\":\"new-password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_VERIFICATION_CODE")));
        mvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationCode\":\"" + passwordCode
                                + "\",\"newPassword\":\"new-password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("密码修改成功")));
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"new-password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sessions/current"));
        mvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
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

    private UserAccount user(String email, String role) {
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("unused-in-contract-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    private MouseDevice publishedMouse() {
        OffsetDateTime now = OffsetDateTime.now();
        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID());
        mouse.setBrand("Contract");
        mouse.setModel("Read Mouse");
        mouse.setVariant("");
        mouse.setSlug("contract-read-mouse-" + mouse.getId());
        mouse.setStatus("PUBLISHED");
        mouse.setSizeCategory("MEDIUM");
        mouse.setLengthMm(new BigDecimal("120"));
        mouse.setWidthMm(new BigDecimal("62"));
        mouse.setHeightMm(new BigDecimal("39"));
        mouse.setWeightG(new BigDecimal("58"));
        mouse.setShapeType("SYMMETRICAL");
        mouse.setSensorName("Test Sensor");
        mouse.setMaxDpi(26000);
        mouse.setMaxPollingRateHz(1000);
        mouse.setConnectionModes("wired");
        mouse.setPrimarySourceUrl("https://example.com/mice/contract-read-mouse");
        mouse.setCreatedAt(now);
        mouse.setUpdatedAt(now);
        return mouse;
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
