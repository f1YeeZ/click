package com.clicker.mousehub;

import com.clicker.mousehub.dto.AuthDtos.ProfileRequest;
import com.clicker.mousehub.dto.ReviewDtos.SupportDab;
import com.clicker.mousehub.dto.ReviewDtos.SupportPositionRequest;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.UserMapper;
import com.clicker.mousehub.service.AuthService;
import com.clicker.mousehub.service.RecommendationService;
import com.clicker.mousehub.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommendationServiceIntegrationTest {
    @Autowired RecommendationService recommendations;
    @Autowired ReviewService reviews;
    @Autowired AuthService auth;
    @Autowired MouseMapper mice;
    @Autowired UserMapper users;
    @Autowired PasswordEncoder encoder;

    @Test void ranksExactBeforeNearMatchesAndExplainsTheDifference() {
        String exactUser = createUser("recommend-exact@example.com", "CLAW");
        String partialOne = createUser("recommend-partial-one@example.com", "CLAW");
        String partialTwo = createUser("recommend-partial-two@example.com", "CLAW");
        MouseDevice exactMouse = mouse("exact-match");
        MouseDevice splitEvidenceMouse = mouse("split-evidence");
        mice.insert(exactMouse);
        mice.insert(splitEvidenceMouse);

        reviews.saveSupportPositions(exactMouse.getId(), exactUser,
                new SupportPositionRequest(List.of("PALM_CENTER", "PALM_HEEL")));
        reviews.saveSupportPositions(splitEvidenceMouse.getId(), partialOne,
                new SupportPositionRequest(List.of("PALM_CENTER")));
        reviews.saveSupportPositions(splitEvidenceMouse.getId(), partialTwo,
                new SupportPositionRequest(List.of("PALM_HEEL")));

        var result = recommendations.recommend("CLAW", List.of("PALM_CENTER", "PALM_HEEL"));
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).mouse().id()).isEqualTo(exactMouse.getId());
        assertThat(result.items().get(0).matchType()).isEqualTo("EXACT");
        assertThat(result.items().get(0).exactMatchCount()).isEqualTo(1);
        assertThat(result.items().get(0).supportCoveragePercent()).isEqualTo(100);
        assertThat(result.items().get(0).explanation()).contains("完整覆盖 2 个期望支撑位置");
        assertThat(result.items().get(0).positionEvidence())
                .containsEntry("PALM_CENTER", 1L).containsEntry("PALM_HEEL", 1L);
        assertThat(result.items().get(1).mouse().id()).isEqualTo(splitEvidenceMouse.getId());
        assertThat(result.items().get(1).matchType()).isEqualTo("NEAR");
        assertThat(result.items().get(1).exactMatchCount()).isZero();
        assertThat(result.items().get(1).supportCoveragePercent()).isEqualTo(50);
        assertThat(result.items().get(1).explanation()).contains("相近匹配").contains("覆盖 1/2");
    }

    @Test void shapeMatchingRejectsAMuchLargerPaintedAreaEvenWhenItContainsTheRequestedShape() {
        String user = createUser("recommend-shape@example.com", "CLAW");
        MouseDevice sameShapeMouse = mouse("same-painted-shape");
        MouseDevice oversizedShapeMouse = mouse("oversized-painted-shape");
        mice.insert(sameShapeMouse);
        mice.insert(oversizedShapeMouse);

        SupportDab requestedDab = new SupportDab(500, 620, 55, "PAINT");
        reviews.saveSupportPositions(sameShapeMouse.getId(), user,
                new SupportPositionRequest(List.of(), List.of(), List.of(requestedDab)));
        reviews.saveSupportPositions(oversizedShapeMouse.getId(), user,
                new SupportPositionRequest(List.of(), List.of(),
                        List.of(new SupportDab(500, 620, 190, "PAINT"))));

        var result = recommendations.recommendShape("CLAW", List.of(requestedDab));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).mouse().id()).isEqualTo(sameShapeMouse.getId());
        assertThat(result.items().get(0).matchType()).isEqualTo("EXACT");
        assertThat(result.items().get(0).shapeSimilarityPercent()).isEqualTo(100);
        assertThat(result.items().get(0).matchedSupportCells()).isNotEmpty();
        assertThat(result.items().get(0).matchedSupportMaxCount()).isEqualTo(1);
        assertThat(result.items().get(0).matchedSupportSampleCount()).isEqualTo(1);
        assertThat(result.items().get(1).mouse().id()).isEqualTo(oversizedShapeMouse.getId());
        assertThat(result.items().get(1).matchType()).isEqualTo("NEAR");
        assertThat(result.items().get(1).supportCoveragePercent()).isEqualTo(100);
        assertThat(result.items().get(1).shapeSimilarityPercent()).isLessThan(60);
    }

    @Test void usesTheSubmittedGripForShapeRecommendationsInsteadOfTheUsersProfilePreference() {
        String user = createUser("recommend-grip-scope@example.com", "CLAW");
        MouseDevice mouse = mouse("palm-only-painted-shape");
        mice.insert(mouse);

        SupportDab palmDab = new SupportDab(500, 620, 70, "PAINT");
        reviews.saveSupportPositions(mouse.getId(), user, "PALM",
                new SupportPositionRequest(List.of(), List.of(), List.of(palmDab)));

        var palmResult = recommendations.recommendShape("PALM", List.of(palmDab));
        var clawResult = recommendations.recommendShape("CLAW", List.of(palmDab));

        assertThat(palmResult.items()).extracting(item -> item.mouse().id()).contains(mouse.getId());
        assertThat(clawResult.items()).extracting(item -> item.mouse().id()).doesNotContain(mouse.getId());
    }

    private String createUser(String email, String grip) {
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID()); user.setEmail(email); user.setPasswordHash(encoder.encode("password123"));
        user.setRole("USER"); user.setStatus("ACTIVE"); user.setCreatedAt(now); user.setUpdatedAt(now);
        users.insert(user);
        auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.0"), grip));
        return email;
    }

    private MouseDevice mouse(String slug) {
        OffsetDateTime now = OffsetDateTime.now();
        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID()); mouse.setBrand("Test"); mouse.setModel(slug); mouse.setVariant("");
        mouse.setSlug(slug + "-" + mouse.getId()); mouse.setStatus("PUBLISHED"); mouse.setConnectionModes("wired");
        mouse.setCreatedAt(now); mouse.setUpdatedAt(now);
        return mouse;
    }
}
