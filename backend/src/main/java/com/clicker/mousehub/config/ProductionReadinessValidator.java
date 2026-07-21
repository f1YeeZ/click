package com.clicker.mousehub.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

@Component
@Profile("prod")
public class ProductionReadinessValidator {
    private static final String DEVELOPMENT_SECRET = "clicker-index-development-secret-change-before-production-2026";

    @Value("${app.jwt.secret}") private String jwtSecret;
    @Value("${app.cors.allowed-origins}") private String allowedOrigins;
    @Value("${spring.datasource.password}") private String databasePassword;
    @Value("${app.mail.enabled:false}") private boolean mailEnabled;
    @Value("${spring.mail.username:}") private String mailUsername;
    @Value("${spring.mail.password:}") private String mailPassword;
    @Value("${app.mail.from:}") private String mailFrom;
    @Value("${app.images.storage-path}") private String imageStoragePath;

    @PostConstruct
    void validate() {
        require(jwtSecret != null && jwtSecret.getBytes(StandardCharsets.UTF_8).length >= 48,
                "生产环境 JWT_SECRET 至少需要 48 个 UTF-8 字节");
        require(!DEVELOPMENT_SECRET.equals(jwtSecret), "生产环境禁止使用默认 JWT_SECRET");
        require(hasText(databasePassword), "生产环境必须配置 DB_PASSWORD");
        require(hasText(allowedOrigins), "生产环境必须配置 CORS_ALLOWED_ORIGINS");
        boolean secureOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .allMatch(origin -> origin.startsWith("https://") && !origin.contains("localhost"));
        require(secureOrigins, "生产环境 CORS_ALLOWED_ORIGINS 必须全部使用 HTTPS 且不能包含 localhost");
        require(mailEnabled, "生产环境必须启用邮件服务，否则用户无法完成注册");
        require(hasText(mailUsername) && hasText(mailPassword) && hasText(mailFrom),
                "生产环境必须配置完整的邮件账号、授权码和发件地址");
        require(hasText(imageStoragePath) && Path.of(imageStoragePath).isAbsolute(),
                "生产环境 IMAGE_STORAGE_PATH 必须是持久卷中的绝对路径");
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
}
