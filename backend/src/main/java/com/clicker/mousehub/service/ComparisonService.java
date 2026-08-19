package com.clicker.mousehub.service;

import com.clicker.mousehub.dto.MouseDtos.*;
import com.clicker.mousehub.entity.MouseDevice;
import org.springframework.stereotype.Service;

import java.math.*;
import java.util.*;
import java.util.function.Function;

@Service
public class ComparisonService {
    private static final Map<String, String> PUBLIC_VALUE_LABELS = Map.ofEntries(
            Map.entry("SMALL", "小"),
            Map.entry("EXTRA_SMALL", "超小"),
            Map.entry("MEDIUM", "中"),
            Map.entry("LARGE", "大"),
            Map.entry("SYMMETRICAL", "对称"),
            Map.entry("HYBRID", "混合"),
            Map.entry("ERGONOMIC", "人体工学"),
            Map.entry("RIGHT", "右手"),
            Map.entry("LEFT", "左手"),
            Map.entry("AMBIDEXTROUS", "双手"),
            Map.entry("BOTH", "左右手"),
            Map.entry("WIRED", "有线"),
            Map.entry("WIRELESS_2_4G", "2.4G 无线"),
            Map.entry("BLUETOOTH", "蓝牙"),
            Map.entry("OPTICAL", "光学"),
            Map.entry("MECHANICAL", "机械")
    );
    private final MouseService mice;

    public ComparisonService(MouseService mice) { this.mice = mice; }

    public CompareResponse compare(List<UUID> ids) {
        List<MouseDevice> items = mice.publishedInOrder(ids);
        List<CompareRow> rows = new ArrayList<>();
        if (!items.isEmpty()) {
            numeric(rows, "尺寸与重量", "长度", "mm", items, MouseDevice::getLengthMm);
            numeric(rows, "尺寸与重量", "宽度", "mm", items, MouseDevice::getWidthMm);
            numeric(rows, "尺寸与重量", "高度", "mm", items, MouseDevice::getHeightMm);
            numeric(rows, "尺寸与重量", "重量", "g", items, MouseDevice::getWeightG);
            text(rows, "外形", "尺寸分类", items, MouseDevice::getSizeCategory);
            text(rows, "外形", "外形类型", items, MouseDevice::getShapeType);
            text(rows, "外形", "适用手", items, MouseDevice::getHandCompatibility);
            text(rows, "传感器性能", "传感器型号", items, MouseDevice::getSensorName);
            numeric(rows, "传感器性能", "最大 DPI", "DPI", items, m -> number(m.getMaxDpi()));
            numeric(rows, "传感器性能", "最大回报率", "Hz", items, m -> number(m.getMaxPollingRateHz()));
            numeric(rows, "传感器性能", "追踪速度", "IPS", items, m -> number(m.getTrackingSpeedIps()));
            numeric(rows, "传感器性能", "最大加速度", "G", items, MouseDevice::getAccelerationG);
            flag(rows, "传感器性能", "可调传感器位置", items, MouseDevice::getAdjustableSensorPosition);
            text(rows, "按键微动", "微动类型", items, MouseDevice::getSwitchType);
            numeric(rows, "按键微动", "微动寿命", "百万次", items, m -> number(m.getSwitchLifeSpanM()));
            flag(rows, "按键微动", "支持热插拔", items, MouseDevice::getHotSwappableSwitches);
            text(rows, "其他", "连接方式", items, MouseDevice::getConnectionModes);
            text(rows, "其他", "主要材质", items, MouseDevice::getMaterialGeneral);
            text(rows, "其他", "具体材质", items, MouseDevice::getMaterialSpecific);
            text(rows, "其他", "购买渠道", items, MouseDevice::getPurchaseChannels);
        }
        return new CompareResponse(items.stream().map(MouseView::from).toList(), rows);
    }

    private void numeric(List<CompareRow> rows, String group, String label, String unit, List<MouseDevice> items,
                         Function<MouseDevice, BigDecimal> getter) {
        BigDecimal base = getter.apply(items.get(0));
        List<BigDecimal> values = items.stream().map(getter).toList();
        if (values.stream().allMatch(Objects::isNull)) return;
        List<CompareCell> cells = values.stream().map(value -> new CompareCell(format(value), delta(base, value))).toList();
        rows.add(new CompareRow(group, label, unit, cells, values.stream().distinct().count() > 1));
    }

    private void text(List<CompareRow> rows, String group, String label, List<MouseDevice> items,
                      Function<MouseDevice, String> getter) {
        List<String> values = items.stream().map(getter).map(this::publicValue).toList();
        if (values.stream().allMatch(value -> value == null || value.isBlank())) return;
        String base = values.get(0);
        rows.add(new CompareRow(group, label, "", values.stream().map(value -> new CompareCell(
                value == null || value.isBlank() ? "—" : value, Objects.equals(base, value) ? "" : "不同")).toList(),
                values.stream().distinct().count() > 1));
    }

    private void flag(List<CompareRow> rows, String group, String label, List<MouseDevice> items,
                      Function<MouseDevice, Boolean> getter) {
        List<Boolean> values = items.stream().map(getter).toList();
        if (values.stream().allMatch(Objects::isNull)) return;
        Boolean base = values.get(0);
        rows.add(new CompareRow(group, label, "", values.stream().map(value -> new CompareCell(
                value == null ? "—" : value ? "是" : "否", Objects.equals(base, value) ? "" : "不同")).toList(),
                values.stream().distinct().count() > 1));
    }

    public static String delta(BigDecimal base, BigDecimal value) {
        if (base == null || value == null) return "";
        BigDecimal result = value.subtract(base).stripTrailingZeros();
        return result.signum() == 0 ? "" : (result.signum() > 0 ? "+" : "") + result.toPlainString();
    }

    private BigDecimal number(Integer value) { return value == null ? null : BigDecimal.valueOf(value); }
    private String format(BigDecimal value) { return value == null ? "—" : value.stripTrailingZeros().toPlainString(); }

    private String publicValue(String value) {
        if (value == null || value.isBlank()) return value;
        String[] parts = value.trim().split("\\s*[,/|;]\\s*");
        return Arrays.stream(parts)
                .filter(part -> !part.isBlank())
                .map(part -> PUBLIC_VALUE_LABELS.getOrDefault(part.toUpperCase(Locale.ROOT), part))
                .distinct()
                .reduce((left, right) -> left + " / " + right)
                .orElse("");
    }
}
