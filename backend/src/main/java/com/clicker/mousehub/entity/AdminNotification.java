package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("admin_notifications")
public class AdminNotification {
    @TableId(type = IdType.INPUT) private UUID id;
    private String type;
    private String title;
    private String message;
    private String targetType;
    private String targetId;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;
    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public String getType() { return type; } public void setType(String value) { type = value; }
    public String getTitle() { return title; } public void setTitle(String value) { title = value; }
    public String getMessage() { return message; } public void setMessage(String value) { message = value; }
    public String getTargetType() { return targetType; } public void setTargetType(String value) { targetType = value; }
    public String getTargetId() { return targetId; } public void setTargetId(String value) { targetId = value; }
    public OffsetDateTime getReadAt() { return readAt; } public void setReadAt(OffsetDateTime value) { readAt = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}
