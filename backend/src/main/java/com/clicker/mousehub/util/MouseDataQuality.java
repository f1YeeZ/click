package com.clicker.mousehub.util;

import com.clicker.mousehub.entity.MouseDevice;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MouseDataQuality {
    public static final int VERIFICATION_VALID_DAYS = 180;
    private static final int PUBLICATION_FIELD_COUNT = 14;

    private MouseDataQuality() {}

    public static List<String> missingPublicationFields(MouseDevice mouse) {
        List<String> missing = new ArrayList<>();
        requiredText(missing, "brand", mouse.getBrand());
        requiredText(missing, "model", mouse.getModel());
        requiredText(missing, "slug", mouse.getSlug());
        requiredText(missing, "sizeCategory", mouse.getSizeCategory());
        requiredPositive(missing, "lengthMm", mouse.getLengthMm());
        requiredPositive(missing, "widthMm", mouse.getWidthMm());
        requiredPositive(missing, "heightMm", mouse.getHeightMm());
        requiredPositive(missing, "weightG", mouse.getWeightG());
        requiredText(missing, "shapeType", mouse.getShapeType());
        requiredText(missing, "sensorName", mouse.getSensorName());
        requiredPositive(missing, "maxDpi", mouse.getMaxDpi());
        requiredPositive(missing, "maxPollingRateHz", mouse.getMaxPollingRateHz());
        requiredText(missing, "connectionModes", mouse.getConnectionModes());
        if (!isHttpUrl(mouse.getPrimarySourceUrl())) missing.add("primarySourceUrl");
        return List.copyOf(missing);
    }

    public static int qualityPercent(MouseDevice mouse) {
        return (int) Math.round((PUBLICATION_FIELD_COUNT - missingPublicationFields(mouse).size()) * 100.0
                / PUBLICATION_FIELD_COUNT);
    }

    public static String verificationStatus(MouseDevice mouse) {
        if (mouse.getVerifiedAt() == null) return "UNVERIFIED";
        return mouse.getVerifiedAt().isBefore(OffsetDateTime.now().minusDays(VERIFICATION_VALID_DAYS))
                ? "STALE" : "CURRENT";
    }

    public static boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void requiredText(List<String> missing, String field, String value) {
        if (!StringUtils.hasText(value)) missing.add(field);
    }

    private static void requiredPositive(List<String> missing, String field, BigDecimal value) {
        if (value == null || value.signum() <= 0) missing.add(field);
    }

    private static void requiredPositive(List<String> missing, String field, Integer value) {
        if (value == null || value <= 0) missing.add(field);
    }
}
