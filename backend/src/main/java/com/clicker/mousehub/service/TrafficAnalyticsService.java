package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clicker.mousehub.dto.OperationsDtos.PageViewRequest;
import com.clicker.mousehub.entity.PageViewEvent;
import com.clicker.mousehub.mapper.PageViewEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.*;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TrafficAnalyticsService {
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern BOT_USER_AGENT = Pattern.compile(
            "bot|crawler|spider|slurp|headless|lighthouse|preview|monitoring", Pattern.CASE_INSENSITIVE);
    private final PageViewEventMapper pageViews;
    private final byte[] hashKey;

    public TrafficAnalyticsService(PageViewEventMapper pageViews,
                                   @Value("${app.analytics.hash-salt}") String hashSalt) {
        this.pageViews = pageViews;
        this.hashKey = hashSalt.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void record(PageViewRequest request, String userAgent) {
        String path = normalizePath(request.path());
        if (path == null || isBot(userAgent)) return;
        OffsetDateTime now = OffsetDateTime.now(REPORTING_ZONE);
        PageViewEvent event = new PageViewEvent();
        event.setId(UUID.randomUUID());
        event.setVisitorHash(hash(request.visitorId()));
        event.setPath(path);
        event.setViewDate(now.toLocalDate());
        event.setCreatedAt(now);
        pageViews.insert(event);
    }

    public Map<LocalDate, PageViewEventMapper.TrafficDayRow> daily(LocalDate from, LocalDate to) {
        return pageViews.aggregate(from, to).stream().collect(Collectors.toMap(
                PageViewEventMapper.TrafficDayRow::getDate, Function.identity()));
    }

    public TrafficTotals today() {
        LocalDate today = LocalDate.now(REPORTING_ZONE);
        return totals(today, today);
    }

    public TrafficTotals totals(LocalDate from, LocalDate to) {
        PageViewEventMapper.TrafficTotalsRow row = pageViews.totals(from, to);
        return row == null ? new TrafficTotals(0, 0)
                : new TrafficTotals(row.getPageViews(), row.getUniqueVisitors());
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Shanghai")
    public void removeExpiredEvents() {
        LocalDate cutoff = LocalDate.now(REPORTING_ZONE).minusDays(180);
        pageViews.delete(new LambdaQueryWrapper<PageViewEvent>().lt(PageViewEvent::getViewDate, cutoff));
    }

    private String hash(UUID visitorId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(visitorId.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化匿名访问统计", exception);
        }
    }

    private static String normalizePath(String value) {
        if (value == null) return null;
        String path = value.trim();
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int fragment = path.indexOf('#');
        if (fragment >= 0) path = path.substring(0, fragment);
        if (!path.startsWith("/") || path.length() > 240 || path.chars().anyMatch(Character::isISOControl)) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("/admin") || lower.startsWith("/admin/") || lower.equals("/dev") || lower.startsWith("/dev/")) return null;
        return path;
    }

    private static boolean isBot(String userAgent) {
        return userAgent != null && BOT_USER_AGENT.matcher(userAgent).find();
    }

    public record TrafficTotals(long pageViews, long uniqueVisitors) {}
}
