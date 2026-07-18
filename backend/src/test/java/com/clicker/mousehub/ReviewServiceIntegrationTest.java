package com.clicker.mousehub;

import com.clicker.mousehub.dto.AuthDtos.RegisterRequest;
import com.clicker.mousehub.dto.ReviewDtos.ReviewRequest;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.service.*;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewServiceIntegrationTest {
    @Autowired AuthService auth;
    @Autowired ReviewService reviews;
    @Autowired MouseMapper mice;

    @Test void upsertAggregatesAndSoftDeleteRestoresSameReview() {
        String email = "reviewer@example.com";
        auth.register(new RegisterRequest(email, "password123"));
        MouseDevice mouse = mouse();
        mice.insert(mouse);
        ReviewRequest request = new ReviewRequest("CLAW", "MEDIUM", "DAYS_30_TO_179",
                5, 4, 4, 5, 4, List.of("lightweight"), List.of("price_high"));

        var first = reviews.save(mouse.getId(), email, request);
        var second = reviews.save(mouse.getId(), email, request);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(reviews.summary(mouse.getId()).overallAverage()).isEqualByComparingTo(new BigDecimal("4.4"));
        assertThat(reviews.summary(mouse.getId()).sampleCount()).isEqualTo(1);

        reviews.delete(mouse.getId(), email);
        assertThat(reviews.summary(mouse.getId()).sampleCount()).isZero();
        assertThat(reviews.save(mouse.getId(), email, request).id()).isEqualTo(first.id());
    }

    private MouseDevice mouse() {
        OffsetDateTime now = OffsetDateTime.now();
        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID()); mouse.setBrand("Test"); mouse.setModel("MP Unit"); mouse.setVariant("");
        mouse.setSlug("test-mp-unit-" + mouse.getId()); mouse.setStatus("PUBLISHED"); mouse.setConnectionModes("wired");
        mouse.setCreatedAt(now); mouse.setUpdatedAt(now);
        return mouse;
    }
}
