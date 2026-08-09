package com.clicker.mousehub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.entity.Review;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.UserMapper;
import com.clicker.mousehub.mapper.ReviewMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @Autowired ReviewMapper reviews;
    @Autowired PasswordEncoder passwordEncoder;

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
        mvc.perform(post("/api/v1/mouse-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gripStyle":"CLAW",
                                 "dabs":[{"x":500,"y":620,"radius":55,"mode":"PAINT"}]}
                                """))
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
    void publicCatalogTextFiltersIgnoreLetterCase() throws Exception {
        MouseDevice mouse = publishedMouse();
        mouse.setMaterial("ABS Plastic");
        mouse.setSwitchName("Omron Optical");
        mouse.setEncoderName("TTC Gold");
        mouse.setPurchaseChannels("JD Official");
        mice.insert(mouse);

        mvc.perform(get("/api/v1/mice")
                        .param("q", "READ MOUSE")
                        .param("sensorName", "test sensor")
                        .param("material", "abs plastic")
                        .param("switchName", "OMRON OPTICAL")
                        .param("encoderName", "ttc gold")
                        .param("purchaseChannel", "jd official"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(mouse.getId().toString())));
    }

    @Test
    @Transactional
    void publicCatalogKeywordSearchMatchesOnlyMouseModel() throws Exception {
        MouseDevice mouse = publishedMouse();
        mouse.setBrand("QueryScope Brand");
        mouse.setModel("Velocity V");
        mouse.setSensorName("QueryScope Sensor");
        mice.insert(mouse);

        mvc.perform(get("/api/v1/mice").param("q", "velocity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(mouse.getId().toString())));

        mvc.perform(get("/api/v1/mice").param("q", "queryscope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
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

    @Test
    @Transactional
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void maintenanceNoticeCanBeClearedAndDisappearsFromPublicConfig() throws Exception {
        mvc.perform(put("/api/v1/admin/settings/maintenance.notice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"今晚 23:00 维护\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is("今晚 23:00 维护")));

        mvc.perform(put("/api/v1/admin/settings/maintenance.notice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", is("")));

        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenanceNotice", is("")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void incompleteDraftCanBeSavedButCannotBePublished() throws Exception {
        String response = mvc.perform(post("/api/v1/admin/mice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brand":"Draft Brand","model":"Draft Model","variant":"",
                                 "slug":"draft-model","status":"DRAFT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.publicationReady", is(false)))
                .andExpect(jsonPath("$.dataQualityPercent", lessThan(100)))
                .andExpect(jsonPath("$.missingPublicationFields", hasItem("primarySourceUrl")))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();

        mvc.perform(patch("/api/v1/admin/mice/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("MOUSE_PUBLICATION_INCOMPLETE")))
                .andExpect(jsonPath("$.error.message", containsString("发布前请补全")));
        mvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miceIncomplete", is(1)))
                .andExpect(jsonPath("$.dataQualityPercent", lessThan(100)));
    }

    @Test
    @Transactional
    void catalogRatingSortPlacesReliableSamplesFirstAndExposesDistribution() throws Exception {
        MouseDevice reliableHigh = publishedMouse(); reliableHigh.setModel("Reliable High"); reliableHigh.setSlug("reliable-high");
        MouseDevice reliableLow = publishedMouse(); reliableLow.setModel("Reliable Low"); reliableLow.setSlug("reliable-low");
        MouseDevice tinyPerfect = publishedMouse(); tinyPerfect.setModel("Tiny Perfect"); tinyPerfect.setSlug("tiny-perfect");
        mice.insert(reliableHigh); mice.insert(reliableLow); mice.insert(tinyPerfect);
        for (int i = 0; i < 5; i++) addReview(reliableHigh, "high-" + i + "@example.com", 8);
        for (int i = 0; i < 5; i++) addReview(reliableLow, "low-" + i + "@example.com", 7);
        addReview(tinyPerfect, "perfect@example.com", 10);

        mvc.perform(get("/api/v1/mice").param("sort", "rating_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is(reliableHigh.getId().toString())))
                .andExpect(jsonPath("$.items[0].averageScore", is(8.0)))
                .andExpect(jsonPath("$.items[0].reviewCount", is(5)))
                .andExpect(jsonPath("$.items[2].id", is(tinyPerfect.getId().toString())))
                .andExpect(jsonPath("$.items[2].lowReviewSample", is(true)));

        mvc.perform(get("/api/v1/mice/" + reliableHigh.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewSummary.sampleCount", is(5)))
                .andExpect(jsonPath("$.reviewSummary.scoreDistribution.8", is(5)))
                .andExpect(jsonPath("$.reviewSummary.lastUpdatedAt", not(emptyOrNullString())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "operator@example.com", roles = "ADMIN")
    void csvImportRequiresPreviewAndIsIdempotent() throws Exception {
        String csv = "brand,model,variant,slug,status,sizeCategory,lengthMm,widthMm,heightMm,weightG,shapeType,sensorName,maxDpi,maxPollingRateHz,connectionModes,primarySourceUrl\n"
                + "运营品牌,批量型号,,operations-import-mouse,DRAFT,MEDIUM,120,62,39,58,SYMMETRICAL,PAW3395,26000,1000,wired,https://example.com/operations-import-mouse\n";
        MockMultipartFile previewFile = new MockMultipartFile("file", "mice.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String previewJson = mvc.perform(multipart("/api/v1/admin/mice/imports/preview").file(previewFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready", is(true)))
                .andExpect(jsonPath("$.createRows", is(1)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String checksum = json.readTree(previewJson).get("checksum").asText();

        MockMultipartFile commitFile = new MockMultipartFile("file", "mice.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/admin/mice/imports").file(commitFile).param("checksum", checksum))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount", is(1)))
                .andExpect(jsonPath("$.alreadyImported", is(false)));

        MockMultipartFile retryFile = new MockMultipartFile("file", "mice.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/admin/mice/imports").file(retryFile).param("checksum", checksum))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyImported", is(true)));
        mvc.perform(get("/api/v1/admin/mice").param("q", "operations-import-mouse"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status", is("DRAFT")));
        mvc.perform(get("/api/v1/admin/audit-logs").param("entityType", "MOUSE_IMPORT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action", is("MOUSE_CSV_IMPORT")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "operator@example.com", roles = "ADMIN")
    void userRolesBansAndReviewModerationAreProtectedAndAudited() throws Exception {
        UserAccount operator = user("operator@example.com", "ADMIN");
        users.insert(operator);
        UserAccount user = user("managed-user@example.com", "USER");
        users.insert(user);
        MouseDevice mouse = publishedMouse();
        mice.insert(mouse);
        Review review = new Review();
        OffsetDateTime now = OffsetDateTime.now();
        review.setId(UUID.randomUUID()); review.setUserId(user.getId()); review.setMouseId(mouse.getId());
        review.setOverallScore(new BigDecimal("8.0")); review.setStatus("ACTIVE"); review.setVersion(0L);
        review.setCreatedAt(now); review.setUpdatedAt(now); reviews.insert(review);

        mvc.perform(patch("/api/v1/admin/users/" + user.getId() + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/users/" + user.getId() + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"负责数据维护\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("ADMIN")));
        mvc.perform(get("/api/v1/admin/users").param("q", "managed-user").param("role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(user.getId().toString())));
        mvc.perform(patch("/api/v1/admin/users/" + user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"权限异常\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code", is("ADMIN_STATUS_PROTECTED")));
        mvc.perform(patch("/api/v1/admin/users/" + user.getId() + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"结束数据维护\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role", is("USER")));
        mvc.perform(patch("/api/v1/admin/users/" + operator.getId() + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"误操作测试\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code", is("SELF_ROLE_CHANGE_FORBIDDEN")));

        mvc.perform(patch("/api/v1/admin/users/" + user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code", is("STATUS_REASON_REQUIRED")));
        mvc.perform(patch("/api/v1/admin/users/" + user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"异常登录复核\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("DISABLED")))
                .andExpect(jsonPath("$.statusReason", is("异常登录复核")))
                .andExpect(jsonPath("$.statusChangedBy", is("operator@example.com")))
                .andExpect(jsonPath("$.statusChangedAt", not(emptyOrNullString())));
        mvc.perform(patch("/api/v1/admin/users/" + user.getId() + "/role").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"错误提权测试\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code", is("ADMIN_ROLE_REQUIRES_ACTIVE_USER")));
        mvc.perform(patch("/api/v1/admin/users/" + user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"复核后解除封禁\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.statusReason", is("复核后解除封禁")));

        mvc.perform(patch("/api/v1/admin/reviews/" + review.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code", is("MODERATION_REASON_REQUIRED")));
        mvc.perform(patch("/api/v1/admin/reviews/" + review.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"异常评分模式\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationReason", is("异常评分模式")))
                .andExpect(jsonPath("$.moderatedBy", is("operator@example.com")));
        mvc.perform(get("/api/v1/admin/audit-logs").param("q", "异常评分模式"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].entityType", is("REVIEW")));
        mvc.perform(get("/api/v1/admin/audit-logs").param("q", "负责数据维护"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action", is("USER_ROLE_CHANGE")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "external-root@example.com", roles = "ADMIN")
    void lastActiveAdministratorCannotBeDemoted() throws Exception {
        users.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getRole, "ADMIN"))
                .forEach(existing -> { existing.setRole("USER"); users.updateById(existing); });
        UserAccount lastAdmin = user("last-admin@example.com", "ADMIN");
        users.insert(lastAdmin);
        mvc.perform(patch("/api/v1/admin/users/" + lastAdmin.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"降级测试\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("LAST_ADMIN_PROTECTED")));
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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_REQUIRED")));
    }

    @Test
    @Transactional
    void administratorCanUseFrontendWithoutBypassingAdminSecondFactor() throws Exception {
        UserAccount administrator = user("front-admin@example.com", "ADMIN");
        administrator.setPasswordHash(passwordEncoder.encode("password123"));
        users.insert(administrator);

        String frontendResponse = mvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"front-admin@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", containsString("clicker_refresh=")))
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.role", is("USER")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(mail.codeFor("ADMIN_LOGIN")).isNull();

        String frontendToken = json.readTree(frontendResponse).get("token").asText();
        mvc.perform(get("/api/v1/admin/analytics").header("Authorization", "Bearer " + frontendToken))
                .andExpect(status().isForbidden());

        String challengeResponse = mvc.perform(post("/api/v1/admin-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"front-admin@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId", not(emptyString())))
                .andReturn().getResponse().getContentAsString();
        String challengeId = json.readTree(challengeResponse).get("challengeId").asText();
        String verificationCode = mail.codeFor("ADMIN_LOGIN");
        org.assertj.core.api.Assertions.assertThat(verificationCode).matches("\\d{6}");

        String adminResponse = mvc.perform(post("/api/v1/admin-sessions/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"email\":\"front-admin@example.com\",\"code\":\""
                                + verificationCode + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", containsString("clicker_admin_refresh=")))
                .andExpect(jsonPath("$.user.role", is("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        String adminToken = json.readTree(adminResponse).get("token").asText();
        mvc.perform(get("/api/v1/admin/analytics").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void forgottenPasswordCanBeResetWithoutAnActiveSession() throws Exception {
        UserAccount account = user("forgot-password@example.com", "USER");
        account.setPasswordHash(passwordEncoder.encode("old-password123"));
        users.insert(account);

        String knownResponse = mvc.perform(post("/api/v1/password-reset-verification-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-password@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/password-reset-verification-codes/current"))
                .andExpect(jsonPath("$.message", is("如果该邮箱已注册，重置验证码将发送至邮箱")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String resetCode = mail.codeFor(EmailVerificationService.RESET_PASSWORD);

        String unknownResponse = mvc.perform(post("/api/v1/password-reset-verification-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("如果该邮箱已注册，重置验证码将发送至邮箱")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(json.readTree(unknownResponse))
                .isEqualTo(json.readTree(knownResponse));

        mvc.perform(put("/api/v1/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-password@example.com\",\"verificationCode\":\""
                                + resetCode + "\",\"newPassword\":\"new-password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("密码重置成功，请使用新密码登录")));

        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-password@example.com\",\"password\":\"old-password123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-password@example.com\",\"password\":\"new-password123\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgot-password@example.com\",\"verificationCode\":\""
                                + resetCode + "\",\"newPassword\":\"another-password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_VERIFICATION_CODE")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void completeAdminOperationsExposeClosedLoopEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics").param("days", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days", is(7)))
                .andExpect(jsonPath("$.points", hasSize(7)));
        String brand = "{\"name\":\"Test Brand\",\"officialUrl\":\"https://example.com\",\"status\":\"ACTIVE\"}";
        mvc.perform(post("/api/v1/admin/brand-profiles").contentType(MediaType.APPLICATION_JSON).content(brand))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name", is("Test Brand")));
        mvc.perform(get("/api/v1/admin/brand-profiles"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].name", hasItem("Test Brand")));
        mvc.perform(put("/api/v1/admin/settings/registration.enabled").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"false\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value", is("false")));
        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registrationEnabled", is(false)));
        mvc.perform(get("/api/v1/admin/exports/users"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", containsString("text/csv")));
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

    private void addReview(MouseDevice mouse, String email, int score) {
        UserAccount user = user(email, "USER");
        users.insert(user);
        OffsetDateTime now = OffsetDateTime.now();
        Review review = new Review();
        review.setId(UUID.randomUUID()); review.setUserId(user.getId()); review.setMouseId(mouse.getId());
        review.setGripStyle("CLAW"); review.setComfortScore(score);
        review.setOverallScore(BigDecimal.valueOf(score)); review.setStatus("ACTIVE"); review.setVersion(0L);
        review.setCreatedAt(now); review.setUpdatedAt(now); reviews.insert(review);
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
