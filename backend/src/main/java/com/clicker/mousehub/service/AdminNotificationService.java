package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.dto.OperationsDtos.NotificationView;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.entity.AdminNotification;
import com.clicker.mousehub.mapper.AdminNotificationMapper;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AdminNotificationService {
    private final AdminNotificationMapper notifications;
    public AdminNotificationService(AdminNotificationMapper notifications) { this.notifications = notifications; }
    public void create(String type, String title, String message, String targetType, Object targetId) {
        AdminNotification value = new AdminNotification(); value.setId(UUID.randomUUID()); value.setType(type);
        value.setTitle(title); value.setMessage(message); value.setTargetType(targetType);
        value.setTargetId(targetId == null ? null : targetId.toString()); value.setCreatedAt(OffsetDateTime.now());
        notifications.insert(value);
    }
    public PageResponse<NotificationView> list(boolean unreadOnly, long page) {
        Page<AdminNotification> result = notifications.selectPage(new Page<>(Math.max(1, page), 20),
                new LambdaQueryWrapper<AdminNotification>().isNull(unreadOnly, AdminNotification::getReadAt)
                        .orderByDesc(AdminNotification::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(NotificationView::from).toList(),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }
    public long unreadCount() { return notifications.selectCount(new LambdaQueryWrapper<AdminNotification>().isNull(AdminNotification::getReadAt)); }
    public void read(UUID id) { AdminNotification value = notifications.selectById(id); if (value != null && value.getReadAt() == null) { value.setReadAt(OffsetDateTime.now()); notifications.updateById(value); } }
    public void readAll() {
        notifications.selectList(new LambdaQueryWrapper<AdminNotification>().isNull(AdminNotification::getReadAt)).forEach(value -> {
            value.setReadAt(OffsetDateTime.now()); notifications.updateById(value);
        });
    }
}
