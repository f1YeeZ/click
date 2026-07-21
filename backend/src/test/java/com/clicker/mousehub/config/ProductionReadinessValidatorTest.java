package com.clicker.mousehub.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionReadinessValidatorTest {
    @Test void rejectsDevelopmentSecrets() {
        ProductionReadinessValidator validator = validValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", "clicker-index-development-secret-change-before-production-2026");
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认 JWT_SECRET");
    }

    @Test void acceptsCompleteSecureProductionConfiguration() {
        validValidator().validate();
    }

    private ProductionReadinessValidator validValidator() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", "a-secure-random-production-secret-with-more-than-forty-eight-bytes-123456");
        ReflectionTestUtils.setField(validator, "allowedOrigins", "https://mouse.example.com");
        ReflectionTestUtils.setField(validator, "databasePassword", "database-password");
        ReflectionTestUtils.setField(validator, "mailEnabled", true);
        ReflectionTestUtils.setField(validator, "mailUsername", "mail@example.com");
        ReflectionTestUtils.setField(validator, "mailPassword", "mail-auth-code");
        ReflectionTestUtils.setField(validator, "mailFrom", "mail@example.com");
        ReflectionTestUtils.setField(validator, "imageStoragePath", Path.of(System.getProperty("java.io.tmpdir"), "mouse-images").toAbsolutePath().toString());
        return validator;
    }
}
