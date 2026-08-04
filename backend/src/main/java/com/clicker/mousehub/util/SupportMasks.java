package com.clicker.mousehub.util;

import com.clicker.mousehub.dto.ReviewDtos.SupportDab;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;

public final class SupportMasks {
    public static final int COLUMNS = 64;
    public static final int ROWS = 96;
    private static final String DAB_PREFIX = "DAB_";

    private SupportMasks() {}

    public static BitSet replayDabs(Collection<SupportDab> dabs) {
        BitSet mask = new BitSet(COLUMNS * ROWS);
        for (SupportDab dab : dabs == null ? List.<SupportDab>of() : dabs) {
            double centerX = dab.x() / 1000.0 * COLUMNS;
            double centerY = dab.y() / 1000.0 * ROWS;
            double radiusX = dab.radius() / 1000.0 * COLUMNS;
            double radiusY = dab.radius() / 1000.0 * ROWS;
            int minX = Math.max(0, (int) Math.floor(centerX - radiusX));
            int maxX = Math.min(COLUMNS - 1, (int) Math.ceil(centerX + radiusX));
            int minY = Math.max(0, (int) Math.floor(centerY - radiusY));
            int maxY = Math.min(ROWS - 1, (int) Math.ceil(centerY + radiusY));
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    double dx = ((x + 0.5) - centerX) / Math.max(radiusX, 0.001);
                    double dy = ((y + 0.5) - centerY) / Math.max(radiusY, 0.001);
                    if (dx * dx + dy * dy <= 1) {
                        mask.set(y * COLUMNS + x, !"ERASE".equals(dab.mode()));
                    }
                }
            }
        }
        return mask;
    }

    public static BitSet fromStoredCodes(Collection<String> codes) {
        List<SupportDab> dabs = (codes == null ? List.<String>of() : codes).stream()
                .filter(code -> code != null && code.startsWith(DAB_PREFIX))
                .sorted()
                .map(SupportMasks::parseDab)
                .filter(java.util.Objects::nonNull)
                .toList();
        return replayDabs(dabs);
    }

    private static SupportDab parseDab(String code) {
        String[] parts = code.substring(DAB_PREFIX.length()).split("_");
        if (parts.length != 5) return null;
        try {
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int radius = Integer.parseInt(parts[4]);
            return new SupportDab(x, y, radius, "E".equals(parts[1]) ? "ERASE" : "PAINT");
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
