package com.clicker.mousehub.security;

import com.clicker.mousehub.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SecurityRateLimitFilter extends OncePerRequestFilter {
    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/v1/sessions", "auth-login", 10, Duration.ofMinutes(5)),
            new Rule("POST", "/api/v1/registration-verification-codes", "auth-register-code", 5, Duration.ofMinutes(10)),
            new Rule("POST", "/api/v1/users", "auth-register", 10, Duration.ofMinutes(10)),
            new Rule("POST", "/api/v1/password-verification-codes", "auth-password-code", 5, Duration.ofMinutes(10)),
            new Rule("PUT", "/api/v1/users/me/password", "auth-password", 10, Duration.ofMinutes(10)),
            new Rule("POST", "/api/v1/password-reset-verification-codes", "auth-password-reset-code", 5, Duration.ofMinutes(10)),
            new Rule("PUT", "/api/v1/password-reset", "auth-password-reset", 10, Duration.ofMinutes(10)),
            new Rule("*", "/api/v1/mice/[0-9a-fA-F-]{36}/reviews/mine(?:/grip-scores/[A-Z]+|/support-positions)?",
                    "review-write", 10, Duration.ofMinutes(1)),
            new Rule("*", "/api/v1/mouse-recommendations", "recommendations", 30, Duration.ofMinutes(1))
    );

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SecurityRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ruleFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = ruleFor(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String key = rule.bucket() + ':' + address;
        AtomicReference<Decision> decision = new AtomicReference<>();
        windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= rule.duration().toMillis()) {
                decision.set(new Decision(true, rule.duration().toSeconds()));
                return new Window(now, 1, rule.duration().toMillis());
            }
            long retryAfter = Math.max(1, (current.startedAt() + current.durationMs() - now + 999) / 1000);
            if (current.count() >= rule.limit()) {
                decision.set(new Decision(false, retryAfter));
                return current;
            }
            decision.set(new Decision(true, retryAfter));
            return new Window(current.startedAt(), current.count() + 1, current.durationMs());
        });

        if (decision.get().allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(decision.get().retryAfterSeconds()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of("RATE_LIMITED", "请求过于频繁，请稍后重试"));
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void removeExpiredWindows() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= entry.getValue().durationMs());
    }

    private Rule ruleFor(HttpServletRequest request) {
        return RULES.stream().filter(rule -> (rule.method().equals("*") || rule.method().equals(request.getMethod()))
                && request.getRequestURI().matches(rule.pathPattern())).findFirst().orElse(null);
    }

    private record Rule(String method, String pathPattern, String bucket, int limit, Duration duration) {}
    private record Window(long startedAt, int count, long durationMs) {}
    private record Decision(boolean allowed, long retryAfterSeconds) {}
}
