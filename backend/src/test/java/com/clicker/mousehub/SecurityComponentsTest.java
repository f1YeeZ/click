package com.clicker.mousehub;

import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.security.JwtService;
import com.clicker.mousehub.security.SecurityRateLimitFilter;
import com.clicker.mousehub.service.RealtimeEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityComponentsTest {
    private static final String SECRET = "test-secret-that-is-deliberately-longer-than-thirty-two-bytes";

    @Test
    void jwtRequiresTheConfiguredIssuerAndAudience() {
        JwtService issuer = new JwtService(SECRET, 24, "clicker-index", "clicker-index-web");
        UserAccount user = new UserAccount();
        user.setEmail("user@example.com");
        user.setRole("USER");
        String token = issuer.create(user);

        assertThat(issuer.subject(token)).isEqualTo("user@example.com");
        JwtService wrongAudience = new JwtService(SECRET, 24, "clicker-index", "another-client");
        assertThatThrownBy(() -> wrongAudience.subject(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void loginRateLimitReturnsJsonAndRetryAfter() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(json);

        for (int attempt = 0; attempt < 10; attempt++) {
            MockHttpServletResponse response = invokeLogin(filter);
            assertThat(response.getStatus()).isEqualTo(204);
        }
        MockHttpServletResponse limited = invokeLogin(filter);
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(json.readTree(limited.getContentAsString()).at("/error/code").asText()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void realtimePayloadIsLowSensitivityAndConnectionsAreLimitedPerAddress() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        String payload = json.writeValueAsString(new RealtimeEventService.RealtimeEvent(
                "review.changed", UUID.randomUUID(), OffsetDateTime.now()));
        java.util.List<String> fields = new java.util.ArrayList<>();
        json.readTree(payload).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("type", "mouseId", "occurredAt");
        assertThat(payload).doesNotContain("email", "userId", "content", "comfortScore");

        RealtimeEventService events = new RealtimeEventService(2, 1);
        SseEmitter first = events.connect("127.0.0.1");
        assertThatThrownBy(() -> events.connect("127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("连接数量过多");
        first.complete();
    }

    private MockHttpServletResponse invokeLogin(SecurityRateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, targetResponse) ->
                ((jakarta.servlet.http.HttpServletResponse) targetResponse).setStatus(204));
        return response;
    }
}
