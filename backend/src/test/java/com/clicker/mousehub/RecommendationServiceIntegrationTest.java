package com.clicker.mousehub;

import com.clicker.mousehub.dto.AuthDtos.ProfileRequest;
import com.clicker.mousehub.dto.ReviewDtos.GripScoreRequest;
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

    @Test void recommendsOnlyMiceWithOneReviewCoveringEveryRequestedPosition() {
        String exactUser = createUser("recommend-exact@example.com", "CLAW");
        String partialOne = createUser("recommend-partial-one@example.com", "CLAW");
        String partialTwo = createUser("recommend-partial-two@example.com", "CLAW");
        MouseDevice exactMouse = mouse("exact-match");
        MouseDevice splitEvidenceMouse = mouse("split-evidence");
        mice.insert(exactMouse);
        mice.insert(splitEvidenceMouse);

        reviews.saveSupportPositions(exactMouse.getId(), exactUser,
                new SupportPositionRequest(List.of("PALM_CENTER", "PALM_HEEL")));
        reviews.saveGrip(exactMouse.getId(), exactUser, "CLAW", new GripScoreRequest(9));
        reviews.saveSupportPositions(splitEvidenceMouse.getId(), partialOne,
                new SupportPositionRequest(List.of("PALM_CENTER")));
        reviews.saveSupportPositions(splitEvidenceMouse.getId(), partialTwo,
                new SupportPositionRequest(List.of("PALM_HEEL")));

        var result = recommendations.recommend("CLAW", List.of("PALM_CENTER", "PALM_HEEL"));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).mouse().id()).isEqualTo(exactMouse.getId());
        assertThat(result.items().get(0).exactMatchCount()).isEqualTo(1);
        assertThat(result.items().get(0).gripComfortAverage()).isEqualByComparingTo("9.0");
        assertThat(result.items().get(0).positionEvidence())
                .containsEntry("PALM_CENTER", 1L).containsEntry("PALM_HEEL", 1L);
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
