package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("mouse_import_jobs")
public class MouseImportJob {
    @TableId(type = IdType.INPUT)
    private String checksum;
    private String filename;
    private String actorEmail;
    private Integer createdCount;
    private Integer updatedCount;
    private OffsetDateTime createdAt;
    private Integer totalCount;
    private String status;
    private String errorReport;
    private OffsetDateTime completedAt;

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public Integer getCreatedCount() { return createdCount; }
    public void setCreatedCount(Integer createdCount) { this.createdCount = createdCount; }
    public Integer getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(Integer updatedCount) { this.updatedCount = updatedCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer value) { totalCount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getErrorReport() { return errorReport; }
    public void setErrorReport(String value) { errorReport = value; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime value) { completedAt = value; }
}
