package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.common.VerificationCodeException;
import com.clicker.mousehub.dto.AuthDtos.*;
import com.clicker.mousehub.entity.UserAccount;
import com.clicker.mousehub.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private static final String RESET_REQUEST_MESSAGE = "如果该邮箱已注册，重置验证码将发送至邮箱";
    private final UserMapper users;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final AdminLoginChallengeService adminChallenges;
    private final MailService mail;
    private final EmailVerificationService verification;

    public AuthService(UserMapper users, PasswordEncoder encoder, SessionService sessions,
                       AdminLoginChallengeService adminChallenges, MailService mail,
                       EmailVerificationService verification) {
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.adminChallenges = adminChallenges;
        this.mail = mail;
        this.verification = verification;
    }

    public VerificationCodeResponse sendRegistrationCode(EmailRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (find(email) != null) throw new BusinessException("ACCOUNT_UNAVAILABLE", "该邮箱暂不可注册", HttpStatus.CONFLICT);
        return verification.send(email, EmailVerificationService.REGISTER);
    }

    @Transactional(noRollbackFor = VerificationCodeException.class)
    public SessionService.SessionGrant register(RegisterRequest request) {
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
        return sessions.issue(user);
    }

    public VerificationCodeResponse sendPasswordCode(String email) {
        UserAccount user = require(email);
        return verification.send(user.getEmail(), EmailVerificationService.CHANGE_PASSWORD);
    }

    public VerificationCodeResponse sendPasswordResetCode(EmailRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        UserAccount user = find(email);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return verification.response(RESET_REQUEST_MESSAGE);
        }
        VerificationCodeResponse sent = verification.send(email, EmailVerificationService.RESET_PASSWORD);
        return new VerificationCodeResponse(RESET_REQUEST_MESSAGE, sent.expiresInSeconds(), sent.resendAfterSeconds());
    }

    @Transactional(noRollbackFor = VerificationCodeException.class)
    public MessageResponse resetPassword(PasswordResetRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        verification.verifyAndConsume(email, EmailVerificationService.RESET_PASSWORD, request.verificationCode());
        UserAccount user = find(email);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new VerificationCodeException("INVALID_VERIFICATION_CODE", "验证码无效或已过期，请重新获取", HttpStatus.BAD_REQUEST);
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_UNCHANGED", "新密码不能与当前密码相同", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        sessions.invalidateAll(user);
        return new MessageResponse("密码重置成功，请使用新密码登录");
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
        sessions.invalidateAll(user);
        return new MessageResponse("密码修改成功");
    }

    public SessionService.SessionGrant login(LoginRequest request) {
        return sessions.issue(authenticate(request));
    }

    public AdminLoginChallengeService.Challenge beginAdminLogin(LoginRequest request) {
        UserAccount user = authenticate(request);
        if (!"ADMIN".equals(user.getRole())) {
            throw invalidCredentials();
        }
        return adminChallenges.begin(user);
    }

    private UserAccount authenticate(LoginRequest request) {
        UserAccount user = find(UserAccount.normalizeEmail(request.email()));
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return user;
    }

    public SessionService.SessionGrant verifyAdmin(AdminLoginVerificationRequest request) {
        UserAccount user = adminChallenges.verify(request.challengeId(), UserAccount.normalizeEmail(request.email()), request.code());
        return sessions.issueAdmin(user);
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

    private UserView view(UserAccount user) {
        return new UserView(user.getId(), user.getEmail(), user.getRole(), user.getHandSize(), user.getHandLengthCm(), user.getPreferredGripStyle());
    }

    private String handSize(java.math.BigDecimal length) {
        if (length == null) return null;
        if (length.compareTo(new java.math.BigDecimal("17.0")) < 0) return "SMALL";
        if (length.compareTo(new java.math.BigDecimal("19.0")) < 0) return "MEDIUM";
        return "LARGE";
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "账号或密码错误", HttpStatus.UNAUTHORIZED);
    }
}
