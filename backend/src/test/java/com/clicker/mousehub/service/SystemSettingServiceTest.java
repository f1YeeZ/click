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
    void exposesConfigurableAdvertisingInPublicSettings() {
        assertThat(settings.publicSettings().advertisingEnabled()).isFalse();

        settings.update("advertising.enabled", "true");
        settings.update("advertising.left.image-url", "https://cdn.example.com/left.webp");
        settings.update("advertising.left.target-url", "https://example.com/products/left");
        settings.update("advertising.left.alt", "左侧鼠标促销广告");
        settings.update("advertising.right.enabled", "false");

        var result = settings.publicSettings();
        assertThat(result.advertisingEnabled()).isTrue();
        assertThat(result.leftAd().enabled()).isTrue();
        assertThat(result.leftAd().imageUrl()).isEqualTo("https://cdn.example.com/left.webp");
        assertThat(result.leftAd().targetUrl()).isEqualTo("https://example.com/products/left");
        assertThat(result.leftAd().altText()).isEqualTo("左侧鼠标促销广告");
        assertThat(result.rightAd().enabled()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void rejectsUnsafeAdvertisingUrls() {
        assertThatThrownBy(() -> settings.update("advertising.left.target-url", "javascript:alert(1)"))
                .hasMessageContaining("http 或 https");
    }
}
