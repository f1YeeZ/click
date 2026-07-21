package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.AuthDtos.*;
import com.clicker.mousehub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/registration-verification-codes") public ResponseEntity<VerificationCodeResponse> sendRegistrationCode(@Valid @RequestBody EmailRequest request) {
        return ResponseEntity.created(URI.create("/api/v1/registration-verification-codes/current"))
                .body(auth.sendRegistrationCode(request));
    }
    @PostMapping("/users") public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = auth.register(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.user().id())).body(response);
    }
    @PostMapping("/sessions") public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.created(URI.create("/api/v1/sessions/current")).body(auth.login(request));
    }
    @PostMapping("/password-verification-codes") public ResponseEntity<VerificationCodeResponse> sendPasswordCode(Authentication authentication) {
        return ResponseEntity.created(URI.create("/api/v1/password-verification-codes/current"))
                .body(auth.sendPasswordCode(authentication.getName()));
    }
    @PutMapping("/users/me/password") public MessageResponse changePassword(Authentication authentication,
                                                                    @Valid @RequestBody ChangePasswordRequest request) {
        return auth.changePassword(authentication.getName(), request);
    }
    @GetMapping("/users/me") public UserView me(Authentication authentication) { return auth.me(authentication.getName()); }
    @PatchMapping("/users/me") public UserView updateProfile(Authentication authentication, @Valid @RequestBody ProfileRequest request) {
        return auth.updateProfile(authentication.getName(), request);
    }
}
