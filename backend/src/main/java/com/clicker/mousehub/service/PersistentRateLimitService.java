package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.entity.SecurityRateLimitBucket;
import com.clicker.mousehub.mapper.SecurityRateLimitBucketMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class PersistentRateLimitService {
    private final SecurityRateLimitBucketMapper buckets;
    private final String dialect;

    public PersistentRateLimitService(SecurityRateLimitBucketMapper buckets,
                                      @Value("${app.security.rate-limit-dialect:postgres}") String dialect) {
        this.buckets = buckets; this.dialect = dialect;
    }

    @Transactional
    public void check(String rule, String address, String email, int limit, Duration window) {
        OffsetDateTime now = OffsetDateTime.now();
        String key = key(rule, address, email);
        SecurityRateLimitBucket bucket = buckets.selectForUpdate(key);
        if (bucket == null) {
            bucket = new SecurityRateLimitBucket(); bucket.setBucketKey(key); bucket.setRequestCount(1);
            bucket.setWindowStartedAt(now); bucket.setExpiresAt(now.plus(window)); bucket.setUpdatedAt(now);
            int inserted = "h2".equalsIgnoreCase(dialect)
                    ? buckets.insertIfAbsentH2(bucket)
                    : buckets.insertIfAbsentPostgres(bucket);
            if (inserted == 1) return;
            bucket = buckets.selectForUpdate(key);
            if (bucket == null) throw new IllegalStateException("Rate-limit bucket disappeared after concurrent insert");
        }
        if (!bucket.getExpiresAt().isAfter(now)) {
            bucket.setRequestCount(1); bucket.setWindowStartedAt(now); bucket.setExpiresAt(now.plus(window)); bucket.setUpdatedAt(now);
            buckets.updateById(bucket); return;
        }
        if (bucket.getRequestCount() >= limit) {
            throw new BusinessException("RATE_LIMITED", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
        }
        bucket.setRequestCount(bucket.getRequestCount() + 1); bucket.setUpdatedAt(now); buckets.updateById(bucket);
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void removeExpired() {
        buckets.delete(Wrappers.<SecurityRateLimitBucket>lambdaQuery().lt(SecurityRateLimitBucket::getExpiresAt, OffsetDateTime.now()));
    }

    private String key(String rule, String address, String email) {
        String value = rule + "|" + (address == null ? "unknown" : address) + "|" +
                (email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
