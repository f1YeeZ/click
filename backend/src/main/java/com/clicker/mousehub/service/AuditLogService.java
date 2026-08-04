package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.dto.AdminDtos.AuditLogView;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.entity.AuditLog;
import com.clicker.mousehub.mapper.AuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogMapper logs;
    private final ObjectMapper json;

    public AuditLogService(AuditLogMapper logs, ObjectMapper json) {
        this.logs = logs;
        this.json = json;
    }

    public void record(String action, String entityType, Object entityId, String summary,
                       Object before, Object after, String reason) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActorEmail(currentActor());
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : entityId.toString());
        log.setSummary(summary);
        log.setBeforeState(serialize(before));
        log.setAfterState(serialize(after));
        log.setReason(blankToNull(reason));
        log.setCreatedAt(OffsetDateTime.now());
        logs.insert(log);
    }

    public PageResponse<AuditLogView> search(String q, String entityType, long page, long pageSize) {
        return search(q, entityType, null, null, null, page, pageSize);
    }

    public PageResponse<AuditLogView> search(String q, String entityType, String action,
                                             OffsetDateTime from, OffsetDateTime to, long page, long pageSize) {
        String term = q == null ? null : q.trim();
        Page<AuditLog> result = logs.selectPage(new Page<>(Math.max(1, page), safeSize(pageSize)),
                new LambdaQueryWrapper<AuditLog>()
                        .and(term != null && !term.isBlank(), wrapper -> wrapper
                                .like(AuditLog::getActorEmail, term)
                                .or().like(AuditLog::getSummary, term)
                                .or().like(AuditLog::getEntityId, term)
                                .or().like(AuditLog::getReason, term))
                        .eq(entityType != null && !entityType.isBlank(), AuditLog::getEntityType, entityType)
                        .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                        .ge(from != null, AuditLog::getCreatedAt, from)
                        .le(to != null, AuditLog::getCreatedAt, to)
                        .orderByDesc(AuditLog::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(AuditLogView::from).toList(),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    public AuditLogView detail(UUID id) {
        AuditLog value = logs.selectById(id);
        if (value == null) throw new com.clicker.mousehub.common.BusinessException("AUDIT_NOT_FOUND", "审计记录不存在", org.springframework.http.HttpStatus.NOT_FOUND);
        return AuditLogView.from(value);
    }

    public String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null ? "system" : authentication.getName();
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { return "{\"serializationError\":true}"; }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static long safeSize(long size) { return Set.of(12L, 24L, 48L).contains(size) ? size : 12; }
}
