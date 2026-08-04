package com.clicker.mousehub.security;

import com.clicker.mousehub.entity.UserAccount;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration duration;
    private final String issuer;
    private final String audience;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.auth.access-expires-minutes:10}") long accessExpiresMinutes,
                      @Value("${app.jwt.issuer:clicker-index}") String issuer,
                      @Value("${app.jwt.audience:clicker-index-web}") String audience) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        if (accessExpiresMinutes < 1) throw new IllegalStateException("ACCESS_TOKEN_EXPIRES_MINUTES must be positive");
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.duration = Duration.ofMinutes(accessExpiresMinutes);
        this.issuer = issuer;
        this.audience = audience;
    }

    public String create(UserAccount user) {
        return create(user, null, user.getTokenVersion());
    }

    public String create(UserAccount user, UUID sessionId, long tokenVersion) {
        Instant now = Instant.now();
        var builder = Jwts.builder().issuer(issuer).subject(user.getEmail()).audience().add(audience).and()
                .id(UUID.randomUUID().toString()).claim("role", user.getRole()).claim("ver", tokenVersion);
        if (sessionId != null) builder.claim("sid", sessionId.toString());
        return builder
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(duration)))
                .signWith(key).compact();
    }

    public long accessExpiresInSeconds() { return duration.toSeconds(); }

    public JwtPrincipal principal(String token) {
        var claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).requireAudience(audience)
                .clockSkewSeconds(30).build().parseSignedClaims(token).getPayload();
        String session = claims.get("sid", String.class);
        Number version = claims.get("ver", Number.class);
        return new JwtPrincipal(claims.getSubject(), session == null ? null : UUID.fromString(session),
                version == null ? 0L : version.longValue());
    }

    public String subject(String token) {
        return principal(token).email();
    }

    public record JwtPrincipal(String email, UUID sessionId, long tokenVersion) {}
}
