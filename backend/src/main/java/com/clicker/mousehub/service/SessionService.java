package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.AuthDtos.SessionResponse;
import com.clicker.mousehub.entity.AuthSession;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.mapper.AuthSessionMapper;
import com.clicker.mousehub.mapper.UserMapper;
import com.clicker.mousehub.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class SessionService {
    private final AuthSessionMapper sessions;
    private final UserMapper users;
    private final JwtService jwt;
    private final Duration refreshDuration;
    private final SecureRandom random = new SecureRandom();

    public SessionService(AuthSessionMapper sessions, UserMapper users, JwtService jwt,
                          @Value("${app.auth.refresh-expires-days:30}") long refreshDays) {
        if (refreshDays < 1) throw new IllegalStateException("REFRESH_EXPIRES_DAYS must be positive");
        this.sessions = sessions; this.users = users; this.jwt = jwt;
        this.refreshDuration = Duration.ofDays(refreshDays);
    }

    @Transactional
    public SessionGrant issue(UserAccount user) {
        return issue(user, false);
    }

    @Transactional
    public SessionGrant issueAdmin(UserAccount user) {
        if (!"ADMIN".equals(user.getRole())) throw unauthorized();
        return issue(user, true);
    }

    private SessionGrant issue(UserAccount user, boolean adminVerified) {
        OffsetDateTime now = OffsetDateTime.now();
        String raw = randomToken();
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID()); session.setUserId(user.getId());
        session.setRefreshTokenHash(hash(raw)); session.setTokenVersion(user.getTokenVersion());
        session.setAdminVerified(adminVerified);
        session.setExpiresAt(now.plus(refreshDuration)); session.setCreatedAt(now);
        sessions.insert(session);
        return grant(user, session, raw);
    }

    @Transactional
    public SessionGrant refresh(String rawRefreshToken, boolean adminRequired) {
        AuthSession session = find(rawRefreshToken);
        if (session == null || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw unauthorized();
        }
        if (Boolean.TRUE.equals(session.getAdminVerified()) != adminRequired) {
            throw unauthorized();
        }
        UserAccount user = users.selectById(session.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus()) || user.getTokenVersion() != session.getTokenVersion()
                || (adminRequired && !"ADMIN".equals(user.getRole()))) {
            revoke(session); throw unauthorized();
        }
        OffsetDateTime now = OffsetDateTime.now();
        String nextRaw = randomToken();
        session.setRefreshTokenHash(hash(nextRaw)); session.setLastUsedAt(now);
        sessions.updateById(session);
        return grant(user, session, nextRaw);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        AuthSession session = find(rawRefreshToken);
        if (session != null) revoke(session);
    }

    @Transactional
    public void invalidateAll(UserAccount user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.updateById(user);
        sessions.update(null, new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getUserId, user.getId()).isNull(AuthSession::getRevokedAt)
                .set(AuthSession::getRevokedAt, OffsetDateTime.now()));
    }

    public long refreshExpiresInSeconds() { return refreshDuration.toSeconds(); }

    private SessionGrant grant(UserAccount user, AuthSession session, String rawRefresh) {
        String effectiveRole = Boolean.TRUE.equals(session.getAdminVerified()) ? "ADMIN" : "USER";
        SessionResponse response = new SessionResponse(jwt.create(user, session.getId(), user.getTokenVersion(), effectiveRole),
                jwt.accessExpiresInSeconds(), new com.clicker.mousehub.dto.AuthDtos.UserView(
                        user.getId(), user.getEmail(), effectiveRole, user.getHandSize(), user.getHandLengthCm(), user.getPreferredGripStyle()));
        return new SessionGrant(response, rawRefresh, session.getExpiresAt(), session.getId());
    }

    public void attachMetadata(UUID sessionId, String ipAddress, String userAgent) {
        if (sessionId == null) return;
        AuthSession session = sessions.selectById(sessionId); if (session == null) return;
        session.setIpAddress(trim(ipAddress, 64)); session.setUserAgent(trim(userAgent, 500)); sessions.updateById(session);
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) return null; String text = value.trim(); return text.length() <= max ? text : text.substring(0, max);
    }

    private AuthSession find(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return sessions.selectOne(Wrappers.<AuthSession>lambdaQuery().eq(AuthSession::getRefreshTokenHash, hash(raw)));
    }

    private void revoke(AuthSession session) {
        session.setRevokedAt(OffsetDateTime.now()); sessions.updateById(session);
    }

    private String randomToken() {
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private BusinessException unauthorized() {
        return new BusinessException("UNAUTHORIZED", "登录已失效，请重新登录", HttpStatus.UNAUTHORIZED);
    }

    public record SessionGrant(SessionResponse response, String refreshToken, OffsetDateTime refreshExpiresAt, UUID sessionId) {}
}
