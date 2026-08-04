package com.clicker.mousehub.dto;

import com.clicker.mousehub.entity.Review;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.entity.AuditLog;
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
                                  String status, String gripStyle, String handSize, String usageDuration,
                                  java.math.BigDecimal overallScore, Integer comfortScore, Integer clickScore,
                                  Integer scrollScore, Integer buildScore, Integer valueScore, Integer coatingScore,
                                  List<GripScoreView> gripScores, List<String> supportPositions,
                                  String moderationReason, String moderatedBy, OffsetDateTime moderatedAt,
                                  OffsetDateTime createdAt) {
        public static AdminReviewView from(Review review, String userEmail, String mouseName) {
            return new AdminReviewView(review.getId(), review.getUserId(), review.getMouseId(), userEmail, mouseName,
                    review.getStatus(), review.getGripStyle(), review.getHandSize(), review.getUsageDuration(), review.getOverallScore(),
                    review.getComfortScore(), review.getClickScore(), review.getScrollScore(), review.getBuildScore(),
                    review.getValueScore(), review.getCoatingScore(), List.of(), List.of(), review.getModerationReason(),
                    review.getModeratedBy(), review.getModeratedAt(), review.getCreatedAt());
        }
    }

    public record GripScoreView(String gripStyle, Integer comfortScore) {}

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
