package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("reviews")
public class Review {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID userId;
    private UUID mouseId;
    private String gripStyle;
    private String handSize;
    private String usageDuration;
    private Integer comfortScore;
    private BigDecimal overallScore;
    private String status;
    private String moderationReason;
    private String moderatedBy;
    private OffsetDateTime moderatedAt;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getMouseId() { return mouseId; }
    public void setMouseId(UUID mouseId) { this.mouseId = mouseId; }
    public String getGripStyle() { return gripStyle; }
    public void setGripStyle(String gripStyle) { this.gripStyle = gripStyle; }
    public String getHandSize() { return handSize; }
    public void setHandSize(String handSize) { this.handSize = handSize; }
    public String getUsageDuration() { return usageDuration; }
    public void setUsageDuration(String usageDuration) { this.usageDuration = usageDuration; }
    public Integer getComfortScore() { return comfortScore; }
    public void setComfortScore(Integer comfortScore) { this.comfortScore = comfortScore; }
    public BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(BigDecimal overallScore) { this.overallScore = overallScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModerationReason() { return moderationReason; }
    public void setModerationReason(String moderationReason) { this.moderationReason = moderationReason; }
    public String getModeratedBy() { return moderatedBy; }
    public void setModeratedBy(String moderatedBy) { this.moderatedBy = moderatedBy; }
    public OffsetDateTime getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(OffsetDateTime moderatedAt) { this.moderatedAt = moderatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
