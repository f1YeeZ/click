package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.common.VerificationCodeException;
import com.clicker.mousehub.dto.AuthDtos.*;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.mapper.UserMapper;
import com.clicker.mousehub.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final MailService mail;
    private final EmailVerificationService verification;

    public AuthService(UserMapper users, PasswordEncoder encoder, JwtService jwt, MailService mail,
                       EmailVerificationService verification) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mail = mail;
        this.verification = verification;
    }

    public VerificationCodeResponse sendRegistrationCode(EmailRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (find(email) != null) throw new BusinessException("ACCOUNT_UNAVAILABLE", "该邮箱暂不可注册", HttpStatus.CONFLICT);
        return verification.send(email, EmailVerificationService.REGISTER);
    }

    @Transactional(noRollbackFor = VerificationCodeException.class)
    public AuthResponse register(RegisterRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (find(email) != null) throw new BusinessException("ACCOUNT_UNAVAILABLE", "该邮箱暂不可注册", HttpStatus.CONFLICT);
        verification.verifyAndConsume(email, EmailVerificationService.REGISTER, request.verificationCode());
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setTermsAcceptedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.insert(user);
        mail.welcome(email);
        return response(user);
    }

    public VerificationCodeResponse sendPasswordCode(String email) {
        UserAccount user = require(email);
        return verification.send(user.getEmail(), EmailVerificationService.CHANGE_PASSWORD);
    }

    @Transactional(noRollbackFor = VerificationCodeException.class)
    public MessageResponse changePassword(String email, ChangePasswordRequest request) {
        UserAccount user = require(email);
        verification.verifyAndConsume(user.getEmail(), EmailVerificationService.CHANGE_PASSWORD, request.verificationCode());
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_UNCHANGED", "新密码不能与当前密码相同", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        users.updateById(user);
        return new MessageResponse("密码修改成功");
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = find(UserAccount.normalizeEmail(request.email()));
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "账号或密码错误", HttpStatus.UNAUTHORIZED);
        }
        return response(user);
    }

    public UserView me(String email) {
        UserAccount user = find(UserAccount.normalizeEmail(email));
        if (user == null) throw new BusinessException("UNAUTHORIZED", "登录已失效", HttpStatus.UNAUTHORIZED);
        return view(user);
    }

    @Transactional
    public UserView updateProfile(String email, ProfileRequest request) {
        UserAccount user = require(email);
        boolean profileLocked = user.getHandLengthCm() != null && user.getPreferredGripStyle() != null;
        if (profileLocked) {
            if (request.handLengthCm() != null && user.getHandLengthCm().compareTo(request.handLengthCm()) != 0) {
                throw new BusinessException("PROFILE_HAND_LENGTH_LOCKED", "个人资料已锁定，手长不可更改", HttpStatus.CONFLICT);
            }
            if (request.preferredGripStyle() != null && !user.getPreferredGripStyle().equals(request.preferredGripStyle())) {
                throw new BusinessException("PROFILE_GRIP_STYLE_LOCKED", "个人资料已锁定，习惯握姿不可更改", HttpStatus.CONFLICT);
            }
        } else {
            if (request.handLengthCm() != null) {
                user.setHandLengthCm(request.handLengthCm());
                user.setHandSize(handSize(request.handLengthCm()));
            }
            if (request.preferredGripStyle() != null) {
                user.setPreferredGripStyle(request.preferredGripStyle());
            }
        }
        user.setUpdatedAt(OffsetDateTime.now());
        users.updateById(user);
        return view(user);
    }

    public UserAccount require(String email) {
        UserAccount user = find(UserAccount.normalizeEmail(email));
        if (user == null || !"ACTIVE".equals(user.getStatus())) throw new BusinessException("UNAUTHORIZED", "登录已失效", HttpStatus.UNAUTHORIZED);
        return user;
    }

    private UserAccount find(String email) {
        return users.selectOne(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getEmail, email));
    }

    private AuthResponse response(UserAccount user) {
        return new AuthResponse(jwt.create(user), view(user));
    }

    private UserView view(UserAccount user) {
        return new UserView(user.getId(), user.getEmail(), user.getRole(), user.getHandSize(), user.getHandLengthCm(), user.getPreferredGripStyle());
    }

    private String handSize(java.math.BigDecimal length) {
        if (length == null) return null;
        if (length.compareTo(new java.math.BigDecimal("17.0")) < 0) return "SMALL";
        if (length.compareTo(new java.math.BigDecimal("19.0")) < 0) return "MEDIUM";
        return "LARGE";
    }
}
