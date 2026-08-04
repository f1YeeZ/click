package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("content_reports")
public class ContentReport {
    @TableId(type = IdType.INPUT) private UUID id;
    private UUID reporterUserId;
    private String reporterEmail;
    private String targetType;
    private UUID targetId;
    private String category;
    private String description;
    private String status;
    private String assigneeEmail;
    private String resolution;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime resolvedAt;
    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getReporterUserId() { return reporterUserId; } public void setReporterUserId(UUID value) { reporterUserId = value; }
    public String getReporterEmail() { return reporterEmail; } public void setReporterEmail(String value) { reporterEmail = value; }
    public String getTargetType() { return targetType; } public void setTargetType(String value) { targetType = value; }
    public UUID getTargetId() { return targetId; } public void setTargetId(UUID value) { targetId = value; }
    public String getCategory() { return category; } public void setCategory(String value) { category = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getAssigneeEmail() { return assigneeEmail; } public void setAssigneeEmail(String value) { assigneeEmail = value; }
    public String getResolution() { return resolution; } public void setResolution(String value) { resolution = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; } public void setResolvedAt(OffsetDateTime value) { resolvedAt = value; }
}
