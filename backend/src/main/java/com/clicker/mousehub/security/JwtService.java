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

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration duration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expires-hours:168}") long expiresHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.duration = Duration.ofHours(expiresHours);
    }

    public String create(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getEmail()).claim("role", user.getRole())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(duration)))
                .signWith(key).compact();
    }

    public String subject(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }
}
