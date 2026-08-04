package com.clicker.mousehub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;

import java.math.BigDecimal;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
                                  @AssertTrue(message = "请先同意用户协议与隐私政策") boolean acceptedTerms) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record EmailRequest(@NotBlank @Email String email) {}
    public record ChangePasswordRequest(@NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record PasswordResetRequest(@NotBlank @Email String email,
                                       @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
                                       @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record MessageResponse(String message) {}
    public record VerificationCodeResponse(String message, long expiresInSeconds, long resendAfterSeconds) {}
    public record UserView(UUID id, String email, String role, String handSize, BigDecimal handLengthCm,
                           String preferredGripStyle) {
        public UserView(UUID id, String email, String role) { this(id, email, role, null, null, null); }
        public UserView(UUID id, String email, String role, String handSize, BigDecimal handLengthCm) {
            this(id, email, role, handSize, handLengthCm, null);
        }
    }
    public record ProfileRequest(@DecimalMin("10.0") @DecimalMax("30.0") @Digits(integer = 2, fraction = 1) BigDecimal handLengthCm,
                                 @Pattern(regexp = "PALM|CLAW|FINGERTIP|MIXED") String preferredGripStyle) {
        public ProfileRequest(BigDecimal handLengthCm) { this(handLengthCm, null); }
    }
    public record AuthResponse(String token, UserView user) {}
    /** `token` is the short-lived access token; refresh credentials are cookie-only. */
    public record SessionResponse(String token, long accessTokenExpiresInSeconds, UserView user) {}
    public record LoginChallengeResponse(boolean secondFactorRequired, UUID challengeId, long expiresInSeconds) {}
    public record AdminLoginVerificationRequest(UUID challengeId,
                                                @NotBlank @Email String email,
                                                @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
}
