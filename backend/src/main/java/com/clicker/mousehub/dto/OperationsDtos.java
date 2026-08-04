package com.clicker.mousehub.dto;

import com.clicker.mousehub.dto.AdminDtos.AdminUserView;
import com.clicker.mousehub.dto.ReviewDtos.GripComfort;
import com.clicker.mousehub.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class OperationsDtos {
    private OperationsDtos() {}

    public record BrandView(UUID id, String name, String officialUrl, String logoUrl, String aliases,
                            String notes, String status, long mouseCount, OffsetDateTime updatedAt) {
        public static BrandView from(BrandProfile brand, long count) {
            return new BrandView(brand.getId(), brand.getName(), brand.getOfficialUrl(), brand.getLogoUrl(),
                    brand.getAliases(), brand.getNotes(), brand.getStatus(), count, brand.getUpdatedAt());
        }
    }
    public record BrandRequest(@NotBlank @Size(max = 80) String name,
                               @Size(max = 500) String officialUrl, @Size(max = 500) String logoUrl,
                               @Size(max = 500) String aliases, @Size(max = 1000) String notes,
                               @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {}

    public record ContentReportView(UUID id, UUID reporterUserId, String reporterEmail, String targetType,
                                    UUID targetId, String targetLabel, String category, String description,
                                    String status, String assigneeEmail, String resolution,
                                    OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime resolvedAt) {}
    public record ReportCreateRequest(@NotBlank @Pattern(regexp = "MOUSE|REVIEW") String targetType,
                                      @NotNull UUID targetId,
                                      @NotBlank @Size(max = 40) String category,
                                      @NotBlank @Size(max = 1000) String description) {}
    public record ReportActionRequest(@NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|REJECTED") String status,
                                      @Size(max = 180) String assigneeEmail,
                                      @Size(max = 1000) String resolution) {}

    public record NotificationView(UUID id, String type, String title, String message, String targetType,
                                   String targetId, boolean read, OffsetDateTime createdAt) {
        public static NotificationView from(AdminNotification value) {
            return new NotificationView(value.getId(), value.getType(), value.getTitle(), value.getMessage(),
                    value.getTargetType(), value.getTargetId(), value.getReadAt() != null, value.getCreatedAt());
        }
    }

    public record SettingView(String key, String value, String description, String updatedBy, OffsetDateTime updatedAt) {}
    public record SettingUpdateRequest(@NotBlank @Size(max = 2000) String value) {}
    public record PublicSettings(String maintenanceNotice, boolean registrationEnabled, boolean reviewSubmissionEnabled) {}

    public record AnalyticsPoint(LocalDate date, long users, long mice, long reviews, long adminActions) {}
    public record AnalyticsResponse(int days, List<AnalyticsPoint> points, long openReports, long unreadNotifications,
                                    long activeSessions, long staleMice) {}

    public record SessionView(UUID id, UUID userId, String userEmail, String ipAddress, String userAgent,
                              boolean active, OffsetDateTime createdAt, OffsetDateTime lastUsedAt,
                              OffsetDateTime expiresAt, OffsetDateTime revokedAt) {}
    public record UserDetailResponse(AdminUserView user, long reviewCount, long activeSessionCount,
                                     List<SessionView> sessions, List<AdminDtos.AuditLogView> recentAudit) {}

    public record BatchStatusRequest(@NotEmpty @Size(max = 100) List<UUID> ids,
                                     @NotBlank String status, @Size(max = 500) String reason) {}
    public record BatchResult(int requested, int changed, List<String> errors) {}
    public record VerificationRequest(@NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|DONE") String status,
                                      @Size(max = 180) String assigneeEmail,
                                      @Size(max = 500) String note,
                                      OffsetDateTime dueAt) {}

    public record ImportJobView(String checksum, String filename, String actorEmail, Integer totalCount,
                                Integer createdCount, Integer updatedCount, String status, boolean hasErrorReport,
                                OffsetDateTime createdAt, OffsetDateTime completedAt) {
        public static ImportJobView from(MouseImportJob value) {
            return new ImportJobView(value.getChecksum(), value.getFilename(), value.getActorEmail(), value.getTotalCount(),
                    value.getCreatedCount(), value.getUpdatedCount(), value.getStatus(),
                    value.getErrorReport() != null && !value.getErrorReport().isBlank(), value.getCreatedAt(), value.getCompletedAt());
        }
    }

    public record PublicReviewView(UUID id, String author, String gripStyle, String handSize, String usageDuration,
                                   BigDecimal comfortAverage, List<GripComfort> gripScores,
                                   OffsetDateTime createdAt) {}
}
