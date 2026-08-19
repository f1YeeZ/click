package com.clicker.mousehub;

import com.clicker.mousehub.dto.AuthDtos.ProfileRequest;
import com.clicker.mousehub.dto.ReviewDtos.SupportPositionRequest;
import com.clicker.mousehub.dto.ReviewDtos.SupportCell;
import com.clicker.mousehub.dto.ReviewDtos.SupportDab;
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
    @Autowired AdminService admin;
    @Autowired MouseMapper mice;
    @Autowired UserMapper users;
    @Autowired PasswordEncoder encoder;

    @Test void pendingAndAuthorDeletedReviewsCannotBeChangedByModerationRaces() {
        String pendingEmail = "pending-reviewer@example.com";
        createUser(pendingEmail);
        auth.updateProfile(pendingEmail, new ProfileRequest(new BigDecimal("18.0"), "CLAW"));
        MouseDevice mouse = mouse();
        mice.insert(mouse);

        UUID pendingId = reviews.saveSupportPositions(mouse.getId(), pendingEmail, "CLAW",
                new SupportPositionRequest(List.of("PALM_CENTER"))).id();
        admin.updateReviewStatus(pendingId, new com.clicker.mousehub.dto.AdminDtos.ModerationRequest("PENDING", "等待复核"));
        assertThatThrownBy(() -> reviews.saveSupportPositions(mouse.getId(), pendingEmail, "PALM",
                new SupportPositionRequest(List.of("PALM_HEEL"))))
                .isInstanceOf(com.clicker.mousehub.common.BusinessException.class)
                .hasMessageContaining("正在审核中");

        admin.updateReviewStatus(pendingId, new com.clicker.mousehub.dto.AdminDtos.ModerationRequest("ACTIVE", "复核完成"));
        reviews.delete(mouse.getId(), pendingEmail);
        assertThat(admin.reviews(pendingEmail, null, 1, 12).items()).isEmpty();
        assertThatThrownBy(() -> admin.updateReviewStatus(pendingId,
                new com.clicker.mousehub.dto.AdminDtos.ModerationRequest("ACTIVE", "误恢复")))
                .isInstanceOf(com.clicker.mousehub.common.BusinessException.class)
                .hasMessageContaining("用户已删除");
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

    @Test void paintedSupportCellsAreDeduplicatedAndAggregatedAsHeatCounts() {
        String firstEmail = "paint-one@example.com";
        String secondEmail = "paint-two@example.com";
        createUser(firstEmail);
        createUser(secondEmail);
        auth.updateProfile(firstEmail, new ProfileRequest(new BigDecimal("18.0"), "CLAW"));
        auth.updateProfile(secondEmail, new ProfileRequest(new BigDecimal("18.5"), "CLAW"));
        MouseDevice mouse = mouse();
        mice.insert(mouse);

        reviews.saveSupportPositions(mouse.getId(), firstEmail,
                new SupportPositionRequest(List.of(), List.of(new SupportCell(10, 18), new SupportCell(10, 18), new SupportCell(11, 18))));
        reviews.saveSupportPositions(mouse.getId(), secondEmail,
                new SupportPositionRequest(List.of(), List.of(new SupportCell(10, 18))));

        var summary = reviews.supportSummary(mouse.getId(), "CLAW", "MEDIUM");
        assertThat(summary.sampleCount()).isEqualTo(2);
        assertThat(summary.maxCount()).isEqualTo(2);
        assertThat(summary.gridColumns()).isEqualTo(64);
        assertThat(summary.gridRows()).isEqualTo(96);
        assertThat(summary.cells()).filteredOn(cell -> cell.x() == 27 && cell.y() == 54)
                .singleElement().satisfies(cell -> {
                    assertThat(cell.count()).isEqualTo(2);
                    assertThat(cell.percentage()).isEqualTo(100);
                });
        assertThat(summary.cells()).filteredOn(cell -> cell.x() == 29 && cell.y() == 54)
                .singleElement().extracting("count").isEqualTo(1L);
        assertThat(reviews.mine(mouse.getId(), firstEmail).supportCells())
                .containsExactly(new SupportCell(10, 18), new SupportCell(11, 18));
        assertThat(reviews.mine(mouse.getId(), firstEmail).supportPositions()).contains("PALM_CENTER");
    }

    @Test void orderedBrushDabsPaintEraseAndAggregateOncePerUser() {
        String firstEmail = "brush-one@example.com";
        String secondEmail = "brush-two@example.com";
        createUser(firstEmail);
        createUser(secondEmail);
        auth.updateProfile(firstEmail, new ProfileRequest(new BigDecimal("18.0"), "CLAW"));
        auth.updateProfile(secondEmail, new ProfileRequest(new BigDecimal("18.5"), "CLAW"));
        MouseDevice mouse = mouse();
        mice.insert(mouse);

        List<SupportDab> firstStroke = List.of(
                new SupportDab(500, 500, 100, "PAINT"),
                new SupportDab(530, 500, 100, "PAINT"),
                new SupportDab(500, 500, 30, "ERASE"));
        reviews.saveSupportPositions(mouse.getId(), firstEmail,
                new SupportPositionRequest(List.of(), List.of(), firstStroke));
        reviews.saveSupportPositions(mouse.getId(), secondEmail,
                new SupportPositionRequest(List.of(), List.of(),
                        List.of(new SupportDab(500, 500, 55, "PAINT"))));

        var mine = reviews.mine(mouse.getId(), firstEmail);
        assertThat(mine.supportDabs()).containsExactlyElementsOf(firstStroke);
        var adminReview = admin.reviews(firstEmail, null, 1, 12).items().get(0);
        assertThat(adminReview.supportDabs()).containsExactlyElementsOf(firstStroke);

        var summary = reviews.supportSummary(mouse.getId(), "CLAW", "MEDIUM");
        assertThat(summary.sampleCount()).isEqualTo(2);
        assertThat(summary.cells()).filteredOn(cell -> cell.x() == 32 && cell.y() == 48)
                .singleElement().extracting("count").isEqualTo(1L);
        assertThat(summary.cells()).filteredOn(cell -> cell.x() == 34 && cell.y() == 48)
                .singleElement().extracting("count").isEqualTo(2L);
        assertThat(summary.cells()).filteredOn(cell -> cell.x() == 38 && cell.y() == 48)
                .singleElement().extracting("count").isEqualTo(1L);
    }

    @Test void supportPaintIsStoredAndAggregatedIndependentlyPerGrip() {
        String email = "per-grip-support@example.com";
        createUser(email);
        auth.updateProfile(email, new ProfileRequest(new BigDecimal("18.0"), "CLAW"));
        MouseDevice mouse = mouse();
        mice.insert(mouse);

        List<SupportDab> clawStroke = List.of(new SupportDab(300, 450, 70, "PAINT"));
        List<SupportDab> palmStroke = List.of(new SupportDab(700, 650, 90, "PAINT"));
        reviews.saveSupportPositions(mouse.getId(), email, "CLAW",
                new SupportPositionRequest(List.of(), List.of(), clawStroke));
        reviews.saveSupportPositions(mouse.getId(), email, "PALM",
                new SupportPositionRequest(List.of(), List.of(), palmStroke));

        var mine = reviews.mine(mouse.getId(), email);
        assertThat(mine.supportByGrip()).extracting("gripStyle")
                .containsExactlyInAnyOrder("CLAW", "PALM");
        assertThat(mine.supportByGrip()).filteredOn(item -> item.gripStyle().equals("CLAW"))
                .singleElement().satisfies(item -> assertThat(item.supportDabs()).containsExactlyElementsOf(clawStroke));
        assertThat(mine.supportByGrip()).filteredOn(item -> item.gripStyle().equals("PALM"))
                .singleElement().satisfies(item -> assertThat(item.supportDabs()).containsExactlyElementsOf(palmStroke));
        assertThat(reviews.supportSummary(mouse.getId()).sampleCount()).isEqualTo(2);
        assertThat(reviews.supportSummary(mouse.getId(), "CLAW", "MEDIUM").sampleCount()).isEqualTo(1);
        assertThat(reviews.supportSummary(mouse.getId(), "PALM", "MEDIUM").sampleCount()).isEqualTo(1);

        reviews.deleteSupportPositions(mouse.getId(), email, "CLAW");
        assertThat(reviews.mine(mouse.getId(), email).supportByGrip()).extracting("gripStyle")
                .containsExactly("PALM");
        assertThat(reviews.supportSummary(mouse.getId(), "CLAW", "MEDIUM").sampleCount()).isZero();
        assertThat(reviews.supportSummary(mouse.getId(), "PALM", "MEDIUM").sampleCount()).isEqualTo(1);
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
