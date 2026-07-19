package com.clicker.mousehub.service;

import com.clicker.mousehub.common.BusinessException;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;

    public MailService(JavaMailSender sender, @Value("${app.mail.enabled:false}") boolean enabled,
                       @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.enabled = enabled;
        this.from = from;
    }

    public void welcome(String recipient) {
        if (!enabled || !StringUtils.hasText(from)) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient);
            message.setSubject("欢迎加入 Clicker Index");
            message.setText("账号创建成功。现在可以提交结构化鼠标评价并保存对比清单。");
            sender.send(message);
        } catch (RuntimeException exception) {
            log.warn("欢迎邮件发送失败，不影响注册：{}", exception.getMessage());
        }
    }

    public void verificationCode(String recipient, String code, long expiresMinutes, String purpose) {
        if (!enabled || !StringUtils.hasText(from)) {
            throw new BusinessException("MAIL_UNAVAILABLE", "邮件服务尚未配置，请联系管理员", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            String action = EmailVerificationService.REGISTER.equals(purpose) ? "注册账号" : "修改密码";
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient);
            message.setSubject("Clicker Index 邮箱验证码");
            message.setText("你正在" + action + "，验证码为：" + code + "\n\n验证码 " + expiresMinutes
                    + " 分钟内有效，请勿转发给他人。如非本人操作，请忽略本邮件。");
            sender.send(message);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("验证码邮件发送失败：{}", exception.getMessage());
            throw new BusinessException("MAIL_SEND_FAILED", "验证码邮件发送失败，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
