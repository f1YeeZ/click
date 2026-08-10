package com.clicker.mousehub.service;

import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.OperationsDtos.*;
import com.clicker.mousehub.entity.SystemSetting;
import com.clicker.mousehub.mapper.SystemSettingMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SystemSettingService {
    private static final Map<String, String> DEFAULTS = Map.of(
            "maintenance.notice", "",
            "registration.enabled", "true",
            "reviews.enabled", "true",
            "upload.max-mb", "5",
            "verification.stale-days", "180",
            "security.session-days", "30");
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "maintenance.notice", "显示在前台顶部的运营公告",
            "registration.enabled", "是否允许新用户注册",
            "reviews.enabled", "是否允许用户提交或修改评价",
            "upload.max-mb", "后台图片上传大小提示（MB）",
            "verification.stale-days", "数据核验过期天数",
            "security.session-days", "登录会话有效天数（配置提示）");
    private final SystemSettingMapper settings;
    private final AuditLogService audit;
    private final RealtimeEventService events;
    public SystemSettingService(SystemSettingMapper settings, AuditLogService audit, RealtimeEventService events) {
        this.settings = settings; this.audit = audit; this.events = events;
    }

    public List<SettingView> list() {
        Map<String, SystemSetting> stored = new HashMap<>();
        settings.selectList(null).forEach(value -> stored.put(value.getSettingKey(), value));
        return DEFAULTS.keySet().stream().sorted().map(key -> view(key, stored.get(key))).toList();
    }

    @Transactional
    public SettingView update(String key, String value) {
        if (!DEFAULTS.containsKey(key)) throw new BusinessException("UNKNOWN_SETTING", "不支持的系统设置", HttpStatus.NOT_FOUND);
        validate(key, value);
        SystemSetting before = settings.selectById(key);
        SystemSetting setting = before == null ? new SystemSetting() : before;
        setting.setSettingKey(key); setting.setSettingValue(value.trim()); setting.setDescription(DESCRIPTIONS.get(key));
        setting.setUpdatedBy(audit.currentActor()); setting.setUpdatedAt(OffsetDateTime.now());
        if (before == null) settings.insert(setting); else settings.updateById(setting);
        audit.record("SYSTEM_SETTING_UPDATE", "SETTING", key, "更新系统设置：" + key, before, setting, null);
        events.publishAfterCommit("settings.changed", null);
        return view(key, setting);
    }

    public PublicSettings publicSettings() {
        return new PublicSettings(value("maintenance.notice"), enabled("registration.enabled"), enabled("reviews.enabled"));
    }
    public boolean enabled(String key) { return Boolean.parseBoolean(value(key)); }
    public String value(String key) {
        SystemSetting setting = settings.selectById(key);
        return setting == null ? DEFAULTS.getOrDefault(key, "") : setting.getSettingValue();
    }
    public void requireEnabled(String key, String message) {
        if (!enabled(key)) throw new BusinessException("FEATURE_DISABLED", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
    private SettingView view(String key, SystemSetting value) {
        return new SettingView(key, value == null ? DEFAULTS.get(key) : value.getSettingValue(), DESCRIPTIONS.get(key),
                value == null ? "system" : value.getUpdatedBy(), value == null ? null : value.getUpdatedAt());
    }
    private void validate(String key, String value) {
        if (value == null) throw new BusinessException("INVALID_SETTING", "设置值不能为空", HttpStatus.BAD_REQUEST);
        if (key.endsWith(".enabled") && !Set.of("true", "false").contains(value.trim().toLowerCase(Locale.ROOT)))
            throw new BusinessException("INVALID_SETTING", "开关值只能为 true 或 false", HttpStatus.BAD_REQUEST);
        if (key.endsWith("-mb") || key.endsWith("-days")) {
            try { if (Integer.parseInt(value.trim()) <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException exception) { throw new BusinessException("INVALID_SETTING", "数值设置必须为正整数", HttpStatus.BAD_REQUEST); }
        }
    }
}
