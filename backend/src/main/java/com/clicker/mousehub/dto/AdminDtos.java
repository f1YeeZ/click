package com.clicker.mousehub.dto;

import com.clicker.mousehub.entity.Review;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.entity.AuditLog;
import com.clicker.mousehub.dto.ReviewDtos.SupportCell;
import com.clicker.mousehub.dto.ReviewDtos.SupportDab;
import com.clicker.mousehub.dto.ReviewDtos.SupportGrip;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record DashboardResponse(long miceTotal, long micePublished, long miceDraft, long miceArchived,
                                    long usersTotal, long usersActive, long usersAdmin, long usersDisabled,
                                    long reviewsTotal, long reviewsActive, long reviewsPending,
                                    int dataQualityPercent, long miceIncomplete, long miceVerificationStale,
                                    List<AdminUserView> recentUsers, List<AdminReviewView> recentReviews,
                                    List<MouseDtos.MouseView> recentMice) {}

    public record AdminUserView(UUID id, String email, String role, String status, String handSize, java.math.BigDecimal handLengthCm,
                                String preferredGripStyle, String statusReason, String statusChangedBy,
                                OffsetDateTime statusChangedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static AdminUserView from(UserAccount user) {
            return new AdminUserView(user.getId(), user.getEmail(), user.getRole(), user.getStatus(), user.getHandSize(),
                    user.getHandLengthCm(), user.getPreferredGripStyle(), user.getStatusReason(), user.getStatusChangedBy(),
                    user.getStatusChangedAt(), user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    public record AdminReviewView(UUID id, UUID userId, UUID mouseId, String userEmail, String mouseName,
                                  String status, String handSize, java.math.BigDecimal comfortAverage,
                                  List<GripScoreView> gripScores, List<String> supportPositions,
                                  List<SupportCell> supportCells, List<SupportDab> supportDabs,
                                  List<SupportGrip> supportByGrip,
                                  String moderationReason, String moderatedBy, OffsetDateTime moderatedAt,
                                  OffsetDateTime createdAt, int gripScoreCount, int supportMarkCount,
                                  long reportCount, long openReportCount, String riskLevel, List<String> riskFlags,
                                  List<ReviewReportView> reports) {
        public static AdminReviewView from(Review review, String userEmail, String mouseName) {
            return new AdminReviewView(review.getId(), review.getUserId(), review.getMouseId(), userEmail, mouseName,
                    review.getStatus(), review.getHandSize(), review.getComfortScore() == null ? java.math.BigDecimal.ZERO
                            : java.math.BigDecimal.valueOf(review.getComfortScore()).setScale(1),
                    List.of(), List.of(), List.of(), List.of(), List.of(), review.getModerationReason(),
                    review.getModeratedBy(), review.getModeratedAt(), review.getCreatedAt(), 0, 0, 0, 0,
                    "LOW", List.of(), List.of());
        }
    }

    public record GripScoreView(String gripStyle, Integer comfortScore) {}
    public record ReviewReportView(UUID id, String category, String description, String status,
                                   String reporterEmail, OffsetDateTime createdAt) {}

    public record AuditLogView(UUID id, String actorEmail, String action, String entityType, String entityId,
                               String summary, String beforeState, String afterState, String reason,
                               OffsetDateTime createdAt) {
        public static AuditLogView from(AuditLog log) {
            return new AuditLogView(log.getId(), log.getActorEmail(), log.getAction(), log.getEntityType(),
                    log.getEntityId(), log.getSummary(), log.getBeforeState(), log.getAfterState(), log.getReason(), log.getCreatedAt());
        }
    }

    public record StatusRequest(@NotBlank String status, @Size(max = 500) String reason) {}
    public record RoleRequest(@NotBlank @Pattern(regexp = "USER|ADMIN") String role,
                              @NotBlank @Size(max = 500) String reason) {}
    public record ModerationRequest(@NotBlank String status,
                                    @Size(max = 500) String reason) {}
}
