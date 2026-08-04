package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@TableName("users")
public class UserAccount {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private String email;
    private String passwordHash;
    private String role;
    private String status;
    private Long tokenVersion;
    private String handSize;
    private BigDecimal handLengthCm;
    private String preferredGripStyle;
    private String statusReason;
    private String statusChangedBy;
    private OffsetDateTime statusChangedAt;
    private OffsetDateTime termsAcceptedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTokenVersion() { return tokenVersion == null ? 0L : tokenVersion; }
    public void setTokenVersion(Long tokenVersion) { this.tokenVersion = tokenVersion; }
    public String getHandSize() { return handSize; }
    public void setHandSize(String handSize) { this.handSize = handSize; }
    public BigDecimal getHandLengthCm() { return handLengthCm; }
    public void setHandLengthCm(BigDecimal handLengthCm) { this.handLengthCm = handLengthCm; }
    public String getPreferredGripStyle() { return preferredGripStyle; }
    public void setPreferredGripStyle(String preferredGripStyle) { this.preferredGripStyle = preferredGripStyle; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public String getStatusChangedBy() { return statusChangedBy; }
    public void setStatusChangedBy(String statusChangedBy) { this.statusChangedBy = statusChangedBy; }
    public OffsetDateTime getStatusChangedAt() { return statusChangedAt; }
    public void setStatusChangedAt(OffsetDateTime statusChangedAt) { this.statusChangedAt = statusChangedAt; }
    public OffsetDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(OffsetDateTime termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
