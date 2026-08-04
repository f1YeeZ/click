package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("security_rate_limit_buckets")
public class SecurityRateLimitBucket {
    @TableId(type = IdType.INPUT)
    private String bucketKey;
    private Integer requestCount;
    private OffsetDateTime windowStartedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime updatedAt;

    public String getBucketKey() { return bucketKey; }
    public void setBucketKey(String bucketKey) { this.bucketKey = bucketKey; }
    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }
    public OffsetDateTime getWindowStartedAt() { return windowStartedAt; }
    public void setWindowStartedAt(OffsetDateTime windowStartedAt) { this.windowStartedAt = windowStartedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
