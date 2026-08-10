package com.clicker.mousehub;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clicker.mousehub.dto.ReviewDtos.GripScoreRequest;
import com.clicker.mousehub.dto.ReviewDtos.SupportDab;
import com.clicker.mousehub.dto.ReviewDtos.SupportPositionRequest;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.entity.Review;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.ReviewMapper;
import com.clicker.mousehub.mapper.UserMapper;
import com.clicker.mousehub.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReviewConcurrencyIntegrationTest {
    @Autowired ReviewService reviewService;
    @Autowired UserMapper users;
    @Autowired MouseMapper mice;
    @Autowired ReviewMapper reviews;

    @Test
    void firstScoreAndSupportMapCanBeSavedConcurrentlyAsOneReviewPackage() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID()); user.setEmail(email); user.setPasswordHash("not-used");
        user.setRole("USER"); user.setStatus("ACTIVE"); user.setHandLengthCm(new BigDecimal("18.0"));
        user.setHandSize("MEDIUM"); user.setPreferredGripStyle("CLAW"); user.setCreatedAt(now); user.setUpdatedAt(now);
        users.insert(user);

        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID()); mouse.setBrand("Concurrent"); mouse.setModel("Review Lock"); mouse.setVariant("");
        mouse.setSlug("concurrent-review-lock-" + mouse.getId()); mouse.setStatus("PUBLISHED");
        mouse.setConnectionModes("wired"); mouse.setCreatedAt(now); mouse.setUpdatedAt(now);
        mice.insert(mouse);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> score = pool.submit(() -> {
                await(ready, start);
                reviewService.saveGrip(mouse.getId(), email, "CLAW", new GripScoreRequest(8));
            });
            Future<?> support = pool.submit(() -> {
                await(ready, start);
                reviewService.saveSupportPositions(mouse.getId(), email, "CLAW",
                        new SupportPositionRequest(List.of(), List.of(),
                                List.of(new SupportDab(500, 500, 80, "PAINT"))));
            });
            ready.await();
            start.countDown();
            score.get();
            support.get();
        } finally {
            pool.shutdownNow();
        }

        var mine = reviewService.mine(mouse.getId(), email);
        assertThat(mine.gripComforts()).extracting("gripStyle").containsExactly("CLAW");
        assertThat(mine.supportByGrip()).extracting("gripStyle").containsExactly("CLAW");
        assertThat(reviews.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, user.getId()).eq(Review::getMouseId, mouse.getId()))).isEqualTo(1);
    }

    private static void await(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
