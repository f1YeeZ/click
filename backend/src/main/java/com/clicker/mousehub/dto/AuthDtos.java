package com.clicker.mousehub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record EmailRequest(@NotBlank @Email String email) {}
    public record ChangePasswordRequest(@NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record MessageResponse(String message) {}
    public record VerificationCodeResponse(String message, long expiresInSeconds, long resendAfterSeconds) {}
    public record UserView(UUID id, String email, String role, String handSize, BigDecimal handLengthCm) {
        public UserView(UUID id, String email, String role) { this(id, email, role, null, null); }
    }
    public record ProfileRequest(@DecimalMin("10.0") @DecimalMax("30.0") @Digits(integer = 2, fraction = 1) BigDecimal handLengthCm) {}
    public record AuthResponse(String token, UserView user) {}
}
