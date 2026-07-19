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
                      @Value("${app.jwt.expires-hours:24}") long expiresHours,
                      @Value("${app.jwt.issuer:clicker-index}") String issuer,
                      @Value("${app.jwt.audience:clicker-index-web}") String audience) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        if (expiresHours < 1) throw new IllegalStateException("JWT_EXPIRES_HOURS must be positive");
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.duration = Duration.ofHours(expiresHours);
        this.issuer = issuer;
        this.audience = audience;
    }

    public String create(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(issuer).subject(user.getEmail()).audience().add(audience).and()
                .id(UUID.randomUUID().toString()).claim("role", user.getRole())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(duration)))
                .signWith(key).compact();
    }

    public String subject(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(issuer).requireAudience(audience)
                .clockSkewSeconds(30).build().parseSignedClaims(token).getPayload().getSubject();
    }
}
