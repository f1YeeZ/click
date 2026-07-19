package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.AuthDtos.*;
import com.clicker.mousehub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register/code") public VerificationCodeResponse sendRegistrationCode(@Valid @RequestBody EmailRequest request) {
        return auth.sendRegistrationCode(request);
    }
    @PostMapping("/register") public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }
    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
    @PostMapping("/password/code") public VerificationCodeResponse sendPasswordCode(Authentication authentication) {
        return auth.sendPasswordCode(authentication.getName());
    }
    @PutMapping("/password") public MessageResponse changePassword(Authentication authentication,
                                                                    @Valid @RequestBody ChangePasswordRequest request) {
        return auth.changePassword(authentication.getName(), request);
    }
    @GetMapping("/me") public UserView me(Authentication authentication) { return auth.me(authentication.getName()); }
    @PutMapping("/me") public UserView updateProfile(Authentication authentication, @Valid @RequestBody ProfileRequest request) {
        return auth.updateProfile(authentication.getName(), request);
    }
}
