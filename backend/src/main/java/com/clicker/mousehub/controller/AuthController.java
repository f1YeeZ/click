package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.AuthDtos.*;
import com.clicker.mousehub.security.RefreshCookieService;
import com.clicker.mousehub.service.AuthService;
import com.clicker.mousehub.service.PersistentRateLimitService;
import com.clicker.mousehub.service.SessionService;
import com.clicker.mousehub.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    private final SessionService sessions;
    private final PersistentRateLimitService limits;
    private final RefreshCookieService cookies;
    private final SystemSettingService settings;

    public AuthController(AuthService auth, SessionService sessions, PersistentRateLimitService limits,
                          RefreshCookieService cookies, SystemSettingService settings) {
        this.auth = auth; this.sessions = sessions; this.limits = limits; this.cookies = cookies; this.settings = settings;
    }

    @PostMapping("/registration-verification-codes")
    public ResponseEntity<VerificationCodeResponse> sendRegistrationCode(@Valid @RequestBody EmailRequest request,
                                                                           HttpServletRequest servletRequest) {
        settings.requireEnabled("registration.enabled", "当前暂停新用户注册");
        limits.check("register-code", servletRequest.getRemoteAddr(), request.email(), 5, Duration.ofMinutes(10));
        return ResponseEntity.created(URI.create("/api/v1/registration-verification-codes/current"))
                .body(auth.sendRegistrationCode(request));
    }

    @PostMapping("/users")
    public ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletRequest servletRequest, HttpServletResponse response) {
        settings.requireEnabled("registration.enabled", "当前暂停新用户注册");
        limits.check("register", servletRequest.getRemoteAddr(), request.email(), 10, Duration.ofMinutes(10));
        SessionService.SessionGrant grant = auth.register(request);
        attach(grant, servletRequest);
        writeSession(response, false, grant);
        return ResponseEntity.created(URI.create("/api/v1/users/" + grant.response().user().id())).body(grant.response());
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                                 HttpServletResponse response) {
        limits.check("login", servletRequest.getRemoteAddr(), request.email(), 10, Duration.ofMinutes(5));
        SessionService.SessionGrant grant = auth.login(request);
        attach(grant, servletRequest); writeSession(response, false, grant);
        return ResponseEntity.created(URI.create("/api/v1/sessions/current")).body(grant.response());
    }

    @PostMapping("/admin-sessions")
    public ResponseEntity<LoginChallengeResponse> loginAdmin(@Valid @RequestBody LoginRequest request,
                                                              HttpServletRequest servletRequest) {
        limits.check("admin-login", servletRequest.getRemoteAddr(), request.email(), 10, Duration.ofMinutes(5));
        var challenge = auth.beginAdminLogin(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LoginChallengeResponse(true, challenge.id(), challenge.expiresInSeconds()));
    }

    @PostMapping("/admin-sessions/verify")
    public ResponseEntity<SessionResponse> verifyAdmin(@Valid @RequestBody AdminLoginVerificationRequest request,
                                                        HttpServletRequest servletRequest, HttpServletResponse response) {
        limits.check("admin-verify", servletRequest.getRemoteAddr(), request.email(), 5, Duration.ofMinutes(10));
        SessionService.SessionGrant grant = auth.verifyAdmin(request);
        attach(grant, servletRequest);
        writeSession(response, true, grant);
        return ResponseEntity.status(HttpStatus.CREATED).body(grant.response());
    }

    @PostMapping("/sessions/refresh")
    public SessionResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        SessionService.SessionGrant grant = sessions.refresh(cookies.read(request, false), false);
        attach(grant, request);
        writeSession(response, false, grant);
        return grant.response();
    }

    @PostMapping("/admin-sessions/refresh")
    public SessionResponse refreshAdmin(HttpServletRequest request, HttpServletResponse response) {
        SessionService.SessionGrant grant = sessions.refresh(cookies.read(request, true), true);
        attach(grant, request);
        writeSession(response, true, grant);
        return grant.response();
    }

    @DeleteMapping("/sessions/current")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(cookies.read(request, false)); cookies.clear(response, false);
    }

    @DeleteMapping("/admin-sessions/current")
    public void logoutAdmin(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(cookies.read(request, true)); cookies.clear(response, true);
    }

    @PostMapping("/password-reset-verification-codes")
    public ResponseEntity<VerificationCodeResponse> sendPasswordResetCode(@Valid @RequestBody EmailRequest request,
                                                                            HttpServletRequest servletRequest) {
        limits.check("password-reset-code", servletRequest.getRemoteAddr(), request.email(), 5, Duration.ofMinutes(10));
        return ResponseEntity.created(URI.create("/api/v1/password-reset-verification-codes/current"))
                .body(auth.sendPasswordResetCode(request));
    }

    @PutMapping("/password-reset")
    public MessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        limits.check("password-reset", servletRequest.getRemoteAddr(), request.email(), 10, Duration.ofMinutes(10));
        return auth.resetPassword(request);
    }

    @PostMapping("/password-verification-codes")
    public ResponseEntity<VerificationCodeResponse> sendPasswordCode(Authentication authentication, HttpServletRequest servletRequest) {
        limits.check("password-code", servletRequest.getRemoteAddr(), authentication.getName(), 5, Duration.ofMinutes(10));
        return ResponseEntity.created(URI.create("/api/v1/password-verification-codes/current"))
                .body(auth.sendPasswordCode(authentication.getName()));
    }

    @PutMapping("/users/me/password")
    public MessageResponse changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request,
                                          HttpServletRequest servletRequest) {
        limits.check("password-change", servletRequest.getRemoteAddr(), authentication.getName(), 10, Duration.ofMinutes(10));
        return auth.changePassword(authentication.getName(), request);
    }

    @GetMapping("/users/me") public UserView me(Authentication authentication) { return auth.me(authentication.getName()); }

    @PatchMapping("/users/me")
    public UserView updateProfile(Authentication authentication, @Valid @RequestBody ProfileRequest request) {
        return auth.updateProfile(authentication.getName(), request);
    }

    private void writeSession(HttpServletResponse response, boolean admin, SessionService.SessionGrant grant) {
        long seconds = Math.max(0, grant.refreshExpiresAt().toEpochSecond() - Instant.now().getEpochSecond());
        cookies.write(response, admin, grant.refreshToken(), Duration.ofSeconds(seconds));
    }
    private void attach(SessionService.SessionGrant grant, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        sessions.attachMetadata(grant.sessionId(), ip, request.getHeader("User-Agent"));
    }
}
