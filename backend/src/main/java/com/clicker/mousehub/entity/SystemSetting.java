package com.clicker.mousehub.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("system_settings")
public class SystemSetting {
    @TableId private String settingKey;
    private String settingValue;
    private String description;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    public String getSettingKey() { return settingKey; } public void setSettingKey(String value) { settingKey = value; }
    public String getSettingValue() { return settingValue; } public void setSettingValue(String value) { settingValue = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public String getUpdatedBy() { return updatedBy; } public void setUpdatedBy(String value) { updatedBy = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
}
