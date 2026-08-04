package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.common.VerificationCodeException;
import com.clicker.mousehub.dto.AuthDtos.VerificationCodeResponse;
import com.clicker.mousehub.entity.EmailVerificationCode;
import com.clicker.mousehub.mapper.EmailVerificationCodeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmailVerificationService {
    public static final String REGISTER = "REGISTER";
    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String RESET_PASSWORD = "RESET_PASSWORD";
    private static final int MAX_ATTEMPTS = 5;

    private final EmailVerificationCodeMapper codes;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final SecureRandom random = new SecureRandom();
    private final Duration validFor;
    private final Duration resendAfter;

    public EmailVerificationService(EmailVerificationCodeMapper codes, PasswordEncoder encoder, MailService mail,
                                    @Value("${app.verification.expires-minutes:10}") long expiresMinutes,
                                    @Value("${app.verification.resend-seconds:60}") long resendSeconds) {
        this.codes = codes;
        this.encoder = encoder;
        this.mail = mail;
        this.validFor = Duration.ofMinutes(expiresMinutes);
        this.resendAfter = Duration.ofSeconds(resendSeconds);
    }

    @Transactional
    public VerificationCodeResponse send(String email, String purpose) {
        OffsetDateTime now = OffsetDateTime.now();
        EmailVerificationCode latest = latest(email, purpose);
        if (latest != null) {
            long remaining = Duration.between(now, latest.getCreatedAt().plus(resendAfter)).toSeconds();
            if (remaining > 0) {
                throw new BusinessException("VERIFICATION_CODE_TOO_FREQUENT", "验证码发送过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
            }
        }

        codes.delete(Wrappers.<EmailVerificationCode>lambdaQuery()
                .eq(EmailVerificationCode::getEmail, email)
                .eq(EmailVerificationCode::getPurpose, purpose));

        String plainCode = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        EmailVerificationCode code = new EmailVerificationCode();
        code.setId(UUID.randomUUID());
        code.setEmail(email);
        code.setPurpose(purpose);
        code.setCodeHash(encoder.encode(plainCode));
        code.setAttempts(0);
        code.setExpiresAt(now.plus(validFor));
        code.setCreatedAt(now);
        codes.insert(code);
        mail.verificationCode(email, plainCode, validFor.toMinutes(), purpose);
        return new VerificationCodeResponse("验证码已发送，请查收邮件", validFor.toSeconds(), resendAfter.toSeconds());
    }

    public void verifyAndConsume(String email, String purpose, String plainCode) {
        EmailVerificationCode code = latest(email, purpose);
        OffsetDateTime now = OffsetDateTime.now();
        if (code == null || code.getConsumedAt() != null || !code.getExpiresAt().isAfter(now)) {
            throw invalid("验证码无效或已过期，请重新获取");
        }
        if (code.getAttempts() >= MAX_ATTEMPTS) {
            throw invalid("验证码尝试次数过多，请重新获取");
        }
        if (!encoder.matches(plainCode, code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            codes.updateById(code);
            String message = code.getAttempts() >= MAX_ATTEMPTS ? "验证码尝试次数过多，请重新获取" : "邮箱验证码错误";
            throw invalid(message);
        }
        code.setConsumedAt(now);
        codes.updateById(code);
    }

    public VerificationCodeResponse response(String message) {
        return new VerificationCodeResponse(message, validFor.toSeconds(), resendAfter.toSeconds());
    }

    private EmailVerificationCode latest(String email, String purpose) {
        return codes.selectOne(Wrappers.<EmailVerificationCode>lambdaQuery()
                .eq(EmailVerificationCode::getEmail, email)
                .eq(EmailVerificationCode::getPurpose, purpose)
                .orderByDesc(EmailVerificationCode::getCreatedAt)
                .last("LIMIT 1"));
    }

    private VerificationCodeException invalid(String message) {
        return new VerificationCodeException("INVALID_VERIFICATION_CODE", message, HttpStatus.BAD_REQUEST);
    }
}
