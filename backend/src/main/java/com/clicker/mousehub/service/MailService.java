package com.clicker.mousehub.service;

import org.slf4j.*;
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
}
