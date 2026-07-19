package com.clicker.mousehub.dto;

import com.clicker.mousehub.entity.Review;
import com.clicker.mousehub.entity.UserAccount;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record DashboardResponse(long miceTotal, long micePublished, long miceDraft,
                                    long usersTotal, long reviewsTotal, long reviewsPending,
                                    List<AdminUserView> recentUsers, List<AdminReviewView> recentReviews,
                                    List<MouseDtos.MouseView> recentMice) {}

    public record AdminUserView(UUID id, String email, String role, String status, String handSize, java.math.BigDecimal handLengthCm,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static AdminUserView from(UserAccount user) {
            return new AdminUserView(user.getId(), user.getEmail(), user.getRole(), user.getStatus(), user.getHandSize(), user.getHandLengthCm(), user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    public record AdminReviewView(UUID id, UUID userId, UUID mouseId, String userEmail, String mouseName,
                                  String status, String gripStyle, String handSize, String usageDuration,
                                  java.math.BigDecimal overallScore, Integer coatingScore, OffsetDateTime createdAt) {
        public static AdminReviewView from(Review review, String userEmail, String mouseName) {
            return new AdminReviewView(review.getId(), review.getUserId(), review.getMouseId(), userEmail, mouseName,
                    review.getStatus(), review.getGripStyle(), review.getHandSize(), review.getUsageDuration(), review.getOverallScore(), review.getCoatingScore(), review.getCreatedAt());
        }
    }

    public record StatusRequest(@NotBlank String status) {}
}
