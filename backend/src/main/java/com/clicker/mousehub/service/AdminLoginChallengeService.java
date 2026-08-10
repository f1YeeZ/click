package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.entity.AdminLoginChallenge;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.mapper.AdminLoginChallengeMapper;
import com.clicker.mousehub.mapper.UserMapper;
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
public class AdminLoginChallengeService {
    private static final int MAX_ATTEMPTS = 5;
    private final AdminLoginChallengeMapper challenges;
    private final UserMapper users;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final SecureRandom random = new SecureRandom();
    private final Duration validFor;

    public AdminLoginChallengeService(AdminLoginChallengeMapper challenges, UserMapper users,
                                      PasswordEncoder encoder, MailService mail,
                                      @Value("${app.verification.expires-seconds:60}") long expiresSeconds) {
        this.challenges = challenges; this.users = users; this.encoder = encoder; this.mail = mail;
        this.validFor = Duration.ofSeconds(expiresSeconds);
    }

    @Transactional
    public Challenge begin(UserAccount user) {
        challenges.delete(Wrappers.<AdminLoginChallenge>lambdaQuery().eq(AdminLoginChallenge::getUserId, user.getId()));
        OffsetDateTime now = OffsetDateTime.now();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        AdminLoginChallenge challenge = new AdminLoginChallenge();
        challenge.setId(UUID.randomUUID()); challenge.setUserId(user.getId()); challenge.setCodeHash(encoder.encode(code));
        challenge.setAttempts(0); challenge.setExpiresAt(now.plus(validFor)); challenge.setCreatedAt(now);
        challenges.insert(challenge);
        mail.verificationCode(user.getEmail(), code, validFor.toSeconds(), "ADMIN_LOGIN");
        return new Challenge(challenge.getId(), validFor.toSeconds());
    }

    @Transactional
    public UserAccount verify(UUID id, String email, String plainCode) {
        AdminLoginChallenge challenge = challenges.selectForUpdate(id);
        UserAccount user = challenge == null ? null : users.selectById(challenge.getUserId());
        if (user == null || !UserAccount.normalizeEmail(email).equals(user.getEmail())
                || !"ADMIN".equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())
                || challenge.getConsumedAt() != null || !challenge.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw invalid();
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) throw invalid();
        if (!encoder.matches(plainCode, challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1); challenges.updateById(challenge);
            throw invalid();
        }
        challenge.setConsumedAt(OffsetDateTime.now()); challenges.updateById(challenge);
        return user;
    }

    private BusinessException invalid() {
        return new BusinessException("INVALID_ADMIN_CODE", "管理员验证码无效或已过期", HttpStatus.UNAUTHORIZED);
    }

    public record Challenge(UUID id, long expiresInSeconds) {}
}
