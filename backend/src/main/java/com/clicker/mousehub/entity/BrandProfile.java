package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("brand_profiles")
public class BrandProfile {
    @TableId(type = IdType.INPUT) private UUID id;
    private String name;
    private String officialUrl;
    private String logoUrl;
    private String aliases;
    private String notes;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public String getName() { return name; } public void setName(String value) { name = value; }
    public String getOfficialUrl() { return officialUrl; } public void setOfficialUrl(String value) { officialUrl = value; }
    public String getLogoUrl() { return logoUrl; } public void setLogoUrl(String value) { logoUrl = value; }
    public String getAliases() { return aliases; } public void setAliases(String value) { aliases = value; }
    public String getNotes() { return notes; } public void setNotes(String value) { notes = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
}
