package com.clicker.mousehub;

import com.clicker.mousehub.dto.OperationsDtos.PageViewRequest;
import com.clicker.mousehub.entity.PageViewEvent;
import com.clicker.mousehub.mapper.PageViewEventMapper;
import com.clicker.mousehub.service.TrafficAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TrafficAnalyticsServiceIntegrationTest {
    @Autowired TrafficAnalyticsService traffic;
    @Autowired PageViewEventMapper pageViews;

    @Test
    void countsPageViewsAndDistinctVisitorsWithoutStoringRawVisitorIds() {
        UUID firstVisitor = UUID.randomUUID();
        UUID secondVisitor = UUID.randomUUID();

        traffic.record(new PageViewRequest(firstVisitor, "/mice?sort=weight"), "Mozilla/5.0");
        traffic.record(new PageViewRequest(firstVisitor, "/mice/example"), "Mozilla/5.0");
        traffic.record(new PageViewRequest(secondVisitor, "/compare"), "Mozilla/5.0");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        TrafficAnalyticsService.TrafficTotals totals = traffic.totals(today, today);
        assertThat(totals.pageViews()).isEqualTo(3);
        assertThat(totals.uniqueVisitors()).isEqualTo(2);

        assertThat(pageViews.selectList(null)).allSatisfy(event -> {
            assertThat(event.getVisitorHash()).hasSize(64).doesNotContain(firstVisitor.toString(), secondVisitor.toString());
            assertThat(event.getPath()).doesNotContain("?");
        });
    }

    @Test
    void ignoresAdminDevelopmentAndBotTraffic() {
        UUID visitor = UUID.randomUUID();
        traffic.record(new PageViewRequest(visitor, "/admin"), "Mozilla/5.0");
        traffic.record(new PageViewRequest(visitor, "/dev/code-map"), "Mozilla/5.0");
        traffic.record(new PageViewRequest(visitor, "/mice"), "Googlebot/2.1");

        assertThat(pageViews.selectCount(null)).isZero();
    }
}
