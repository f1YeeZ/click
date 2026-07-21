package com.clicker.mousehub;

import com.clicker.mousehub.dto.AuthDtos.ProfileRequest;
import com.clicker.mousehub.dto.ReviewDtos.BaseScoreRequest;
import com.clicker.mousehub.dto.ReviewDtos.GripScoreRequest;
import com.clicker.mousehub.dto.ReviewDtos.ReviewRequest;
import com.clicker.mousehub.dto.ReviewDtos.SupportPositionRequest;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.UserMapper;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.service.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewServiceIntegrationTest {
    @Autowired AuthService auth;
    @Autowired ReviewService reviews;
    @Autowired MouseMapper mice;
    @Autowired UserMapper users;
    @Autowired PasswordEncoder encoder;

    @Test void upsertAggregatesAndSoftDeleteRestoresSameReview() {
        String email = "reviewer@example.com";
        createUser(email);
        MouseDevice mouse = mouse();
        mice.insert(mouse);
        ReviewRequest request = new ReviewRequest("CLAW", "MEDIUM", "DAYS_30_TO_179",
                5, 4, 4, 5, 4, List.of("lightweight"), List.of("price_high"));

        var first = reviews.save(mouse.getId(), email, request);
        var second = reviews.save(mouse.getId(), email, request);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(reviews.summary(mouse.getId()).overallAverage()).isEqualByComparingTo(new BigDecimal("4.4"));
        assertThat(reviews.summary(mouse.getId()).baseAverage()).isEqualByComparingTo(new BigDecimal("4.3"));
        assertThat(reviews.summary(mouse.getId()).gripAverage()).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(reviews.summary(mouse.getId()).sampleCount()).isEqualTo(1);

        reviews.delete(mouse.getId(), email);
        assertThat(reviews.summary(mouse.getId()).sampleCount()).isZero();
        assertThat(reviews.save(mouse.getId(), email, request).id()).isEqualTo(first.id());
    }

    @Test void baseScoresAreSubmittedOnceAndComfortIsSubmittedOncePerGrip() {
        String email = "split-reviewer@example.com";
        createUser(email);
        auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.5")));
        MouseDevice mouse = mouse();
        mice.insert(mouse);

        var base = reviews.saveBase(mouse.getId(), email, new BaseScoreRequest(8, 7, 9, 8));
        assertThat(base.baseSubmitted()).isTrue();
        assertThat(base.handLengthCm()).isEqualByComparingTo("18.5");
        assertThatThrownBy(() -> reviews.saveBase(mouse.getId(), email, new BaseScoreRequest(6, 6, 6, 6)))
                .hasMessageContaining("只能提交一次");

        for (String grip : List.of("PALM", "CLAW", "FINGERTIP", "MIXED")) {
            reviews.saveGrip(mouse.getId(), email, grip, new GripScoreRequest(8));
        }
        assertThat(reviews.mine(mouse.getId(), email).gripComforts()).hasSize(4);
        assertThatThrownBy(() -> reviews.saveGrip(mouse.getId(), email, "PALM", new GripScoreRequest(9)))
                .hasMessageContaining("已经评价过");
        assertThat(reviews.summary(mouse.getId(), null, "MEDIUM").sampleCount()).isEqualTo(1);

        reviews.deleteGrip(mouse.getId(), email, "CLAW");
        assertThat(reviews.mine(mouse.getId(), email).gripComforts()).extracting("gripStyle")
                .containsExactlyInAnyOrder("PALM", "FINGERTIP", "MIXED");
        reviews.deleteBase(mouse.getId(), email);
        assertThat(reviews.mine(mouse.getId(), email).baseSubmitted()).isFalse();
        assertThat(reviews.mine(mouse.getId(), email).gripComforts()).hasSize(3);
        reviews.saveBase(mouse.getId(), email, new BaseScoreRequest(9, 9, 9, 9));
        assertThat(reviews.mine(mouse.getId(), email).baseSubmitted()).isTrue();
        assertThat(reviews.mine(mouse.getId(), email).gripComforts()).hasSize(3);
        assertThat(reviews.summary(mouse.getId()).baseAverage()).isEqualByComparingTo(new BigDecimal("9.0"));
        assertThat(reviews.summary(mouse.getId()).gripAverage()).isEqualByComparingTo(new BigDecimal("8.0"));
        assertThat(reviews.summary(mouse.getId()).baseSampleCount()).isEqualTo(1);
        assertThat(reviews.summary(mouse.getId()).gripSampleCount()).isEqualTo(3);

        String smallHandEmail = "small-hand-reviewer@example.com";
        createUser(smallHandEmail);
        auth.updateProfile(smallHandEmail, new ProfileRequest(new BigDecimal("16.5")));
        reviews.saveBase(mouse.getId(), smallHandEmail, new BaseScoreRequest(3, 3, 3, 3));
        var mediumGripFilter = reviews.summary(mouse.getId(), null, "MEDIUM");
        assertThat(mediumGripFilter.baseAverage()).isEqualByComparingTo(new BigDecimal("6.0"));
        assertThat(mediumGripFilter.baseSampleCount()).isEqualTo(2);
        assertThat(mediumGripFilter.gripAverage()).isEqualByComparingTo(new BigDecimal("8.0"));
        assertThat(mediumGripFilter.gripSampleCount()).isEqualTo(3);
        var unmatchedGripFilter = reviews.summary(mouse.getId(), "PALM", "SMALL");
        assertThat(unmatchedGripFilter.baseAverage()).isEqualByComparingTo(new BigDecimal("6.0"));
        assertThat(unmatchedGripFilter.gripSampleCount()).isZero();
    }

    @Test void newBaseSubmissionRestoresAPreviouslyDeletedLegacyReview() {
        String email = "restored-reviewer@example.com";
        createUser(email);
        auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.0")));
        MouseDevice mouse = mouse();
        mice.insert(mouse);
        ReviewRequest legacy = new ReviewRequest("CLAW", "MEDIUM", "DAYS_30_TO_179",
                5, 4, 4, 5, 4, List.of(), List.of());
        UUID originalId = reviews.save(mouse.getId(), email, legacy).id();
        reviews.delete(mouse.getId(), email);

        var restored = reviews.saveBase(mouse.getId(), email, new BaseScoreRequest(9, 8, 7, 6));
        assertThat(restored.id()).isEqualTo(originalId);
        assertThat(restored.baseSubmitted()).isTrue();
        assertThat(restored.gripComforts()).isEmpty();
        assertThat(reviews.summary(mouse.getId()).sampleCount()).isEqualTo(1);
    }

    @Test void gripScoresCanStandAloneAndUsePreferredGripWeight() {
        String email = "weighted-grip-reviewer@example.com";
        createUser(email);
        auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.0"), "CLAW"));
        MouseDevice mouse = mouse();
        mice.insert(mouse);

        reviews.saveGrip(mouse.getId(), email, "CLAW", new GripScoreRequest(10));
        reviews.saveGrip(mouse.getId(), email, "PALM", new GripScoreRequest(2));

        var mine = reviews.mine(mouse.getId(), email);
        assertThat(mine.baseSubmitted()).isFalse();
        assertThat(mine.gripComforts()).hasSize(2);
        assertThat(reviews.summary(mouse.getId()).gripAverage()).isEqualByComparingTo("8.2");
        assertThat(reviews.summary(mouse.getId()).gripLowSample()).isTrue();
    }

    @Test void supportPositionsAreMultiSelectReplaceableAndAggregatedBySelectionRate() {
        String firstEmail = "support-one@example.com";
        String secondEmail = "support-two@example.com";
        createUser(firstEmail);
        createUser(secondEmail);
        auth.updateProfile(firstEmail, new ProfileRequest(new BigDecimal("18.0"), "CLAW"));
        auth.updateProfile(secondEmail, new ProfileRequest(new BigDecimal("16.5"), "PALM"));
        MouseDevice mouse = mouse();
        mice.insert(mouse);

        reviews.saveSupportPositions(mouse.getId(), firstEmail,
                new SupportPositionRequest(List.of("PALM_CENTER", "PALM_HEEL")));
        reviews.saveSupportPositions(mouse.getId(), secondEmail,
                new SupportPositionRequest(List.of("PALM_CENTER")));

        var summary = reviews.supportSummary(mouse.getId());
        assertThat(summary.sampleCount()).isEqualTo(2);
        assertThat(summary.positions()).filteredOn(position -> position.code().equals("PALM_CENTER"))
                .singleElement().satisfies(position -> {
                    assertThat(position.count()).isEqualTo(2);
                    assertThat(position.percentage()).isEqualTo(100);
                });
        assertThat(summary.positions()).filteredOn(position -> position.code().equals("PALM_HEEL"))
                .singleElement().satisfies(position -> {
                    assertThat(position.count()).isEqualTo(1);
                    assertThat(position.percentage()).isEqualTo(50);
                });

        var clawMedium = reviews.supportSummary(mouse.getId(), "CLAW", "MEDIUM");
        assertThat(clawMedium.sampleCount()).isEqualTo(1);
        assertThat(clawMedium.positions()).filteredOn(position -> position.code().equals("PALM_HEEL"))
                .singleElement().extracting("count").isEqualTo(1L);
        var palmSmall = reviews.supportSummary(mouse.getId(), "PALM", "SMALL");
        assertThat(palmSmall.sampleCount()).isEqualTo(1);
        assertThat(palmSmall.positions()).filteredOn(position -> position.code().equals("PALM_HEEL"))
                .singleElement().extracting("count").isEqualTo(0L);

        reviews.saveSupportPositions(mouse.getId(), firstEmail,
                new SupportPositionRequest(List.of("THUMB_BASE")));
        assertThat(reviews.mine(mouse.getId(), firstEmail).supportPositions()).containsExactly("THUMB_BASE");
        assertThat(reviews.supportSummary(mouse.getId()).positions())
                .filteredOn(position -> position.code().equals("PALM_HEEL"))
                .singleElement().extracting("count").isEqualTo(0L);
    }

    @Test void handLengthAndPreferredGripAreImmutableAfterFirstSelection() {
        String email = "locked-profile@example.com";
        createUser(email);

        auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.0"), null));
        auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.5"), null));
        var completed = auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.0"), "CLAW"));
        assertThat(completed.handLengthCm()).isEqualByComparingTo("18.0");
        assertThat(completed.preferredGripStyle()).isEqualTo("CLAW");

        assertThatThrownBy(() -> auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.1"), "CLAW")))
                .hasMessageContaining("个人资料已锁定，手长不可更改");
        assertThatThrownBy(() -> auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.0"), "PALM")))
                .hasMessageContaining("个人资料已锁定，习惯握姿不可更改");
    }

    private MouseDevice mouse() {
        OffsetDateTime now = OffsetDateTime.now();
        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID()); mouse.setBrand("Test"); mouse.setModel("MP Unit"); mouse.setVariant("");
        mouse.setSlug("test-mp-unit-" + mouse.getId()); mouse.setStatus("PUBLISHED"); mouse.setConnectionModes("wired");
        mouse.setCreatedAt(now); mouse.setUpdatedAt(now);
        return mouse;
    }

    private void createUser(String email) {
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID()); user.setEmail(email); user.setPasswordHash(encoder.encode("password123"));
        user.setRole("USER"); user.setStatus("ACTIVE"); user.setCreatedAt(now); user.setUpdatedAt(now);
        users.insert(user);
    }
}
