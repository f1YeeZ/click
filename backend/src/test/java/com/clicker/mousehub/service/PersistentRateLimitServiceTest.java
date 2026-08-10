package com.clicker.mousehub.service;

import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.mapper.SecurityRateLimitBucketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PersistentRateLimitServiceTest {
    @Autowired PersistentRateLimitService limits;
    @Autowired SecurityRateLimitBucketMapper buckets;

    @BeforeEach
    void clearBuckets() { buckets.delete(null); }

    @Test
    void limitSurvivesAcrossCallsAndReturnsTooManyRequests() {
        limits.check("distributed-test", "198.51.100.20", null, 2, Duration.ofMinutes(1));
        limits.check("distributed-test", "198.51.100.20", null, 2, Duration.ofMinutes(1));

        assertThatThrownBy(() -> limits.check(
                "distributed-test", "198.51.100.20", null, 2, Duration.ofMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("RATE_LIMITED");
    }
}
