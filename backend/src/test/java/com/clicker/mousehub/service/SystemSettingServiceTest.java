package com.clicker.mousehub.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.realtime.event-coalesce-ms=60000")
@ActiveProfiles("test")
class SystemSettingServiceTest {
    @Autowired SystemSettingService settings;
    @Autowired RealtimeEventService events;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void updatePublishesAFrontendSettingsInvalidationAfterCommit() {
        var result = settings.update("maintenance.notice", "今晚 23:00 维护");

        assertThat(result.value()).isEqualTo("今晚 23:00 维护");
        assertThat(events.pendingEvent("settings.changed")).isNotNull();
        assertThat(events.pendingEvent("settings.changed").type()).isEqualTo("settings.changed");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void exposesOnlySupportedPublicSettings() {
        var result = settings.publicSettings();
        assertThat(result.registrationEnabled()).isTrue();
        assertThat(result.reviewSubmissionEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void rejectsRemovedAdvertisingSettings() {
        assertThatThrownBy(() -> settings.update("advertising.enabled", "true"))
                .hasMessageContaining("不支持的系统设置");
    }
}
