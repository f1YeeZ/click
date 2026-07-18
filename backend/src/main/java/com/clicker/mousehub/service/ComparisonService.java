package com.clicker.mousehub.service;

import com.clicker.mousehub.dto.MouseDtos.*;
import com.clicker.mousehub.entity.MouseDevice;
import org.springframework.stereotype.Service;

import java.math.*;
import java.util.*;
import java.util.function.Function;

@Service
public class ComparisonService {
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
            text(rows, "外形", "隆起位置", items, MouseDevice::getHumpPlacement);
            text(rows, "外形", "前端外扩", items, MouseDevice::getFrontFlare);
            text(rows, "外形", "侧面曲率", items, MouseDevice::getSideCurvature);
            flag(rows, "外形", "拇指托", items, MouseDevice::getThumbRest);
            flag(rows, "外形", "无名指托", items, MouseDevice::getRingFingerRest);
            text(rows, "性能", "传感器", items, MouseDevice::getSensorName);
            text(rows, "性能", "传感器类型", items, MouseDevice::getSensorType);
            numeric(rows, "性能", "最大 DPI", "DPI", items, m -> number(m.getMaxDpi()));
            numeric(rows, "性能", "最大回报率", "Hz", items, m -> number(m.getMaxPollingRateHz()));
            numeric(rows, "性能", "追踪速度", "IPS", items, m -> number(m.getTrackingSpeedIps()));
            numeric(rows, "性能", "最大加速度", "G", items, MouseDevice::getAccelerationG);
            flag(rows, "性能", "可调传感器位置", items, MouseDevice::getAdjustableSensorPosition);
            numeric(rows, "性能", "传感器位置 X", "", items, MouseDevice::getSensorPositionX);
            numeric(rows, "性能", "传感器位置 Y", "", items, MouseDevice::getSensorPositionY);
            numeric(rows, "按键与滚轮", "总按键数", "", items, m -> number(m.getButtonCount()));
            numeric(rows, "按键与滚轮", "侧键数", "", items, m -> number(m.getSideButtonCount()));
            text(rows, "按键与滚轮", "微动", items, MouseDevice::getSwitchName);
            text(rows, "按键与滚轮", "微动类型", items, MouseDevice::getSwitchType);
            numeric(rows, "按键与滚轮", "微动寿命", "百万次", items, m -> number(m.getSwitchLifeSpanM()));
            flag(rows, "按键与滚轮", "热插拔微动", items, MouseDevice::getHotSwappableSwitches);
            text(rows, "按键与滚轮", "编码器", items, MouseDevice::getEncoderName);
            text(rows, "按键与滚轮", "编码器类型", items, MouseDevice::getEncoderType);
            numeric(rows, "按键与滚轮", "滚轮步数", "", items, m -> number(m.getEncoderSteps()));
            text(rows, "其他", "连接模式", items, MouseDevice::getConnectionModes);
            text(rows, "其他", "材质", items, MouseDevice::getMaterial);
            text(rows, "其他", "通用材质", items, MouseDevice::getMaterialGeneral);
            text(rows, "其他", "具体材质", items, MouseDevice::getMaterialSpecific);
            text(rows, "其他", "购买渠道", items, MouseDevice::getPurchaseChannels);
        }
        return new CompareResponse(items.stream().map(MouseView::from).toList(), rows);
    }

    private void numeric(List<CompareRow> rows, String group, String label, String unit, List<MouseDevice> items,
                         Function<MouseDevice, BigDecimal> getter) {
        BigDecimal base = getter.apply(items.get(0));
        List<BigDecimal> values = items.stream().map(getter).toList();
        List<CompareCell> cells = values.stream().map(value -> new CompareCell(format(value), delta(base, value))).toList();
        rows.add(new CompareRow(group, label, unit, cells, values.stream().distinct().count() > 1));
    }

    private void text(List<CompareRow> rows, String group, String label, List<MouseDevice> items,
                      Function<MouseDevice, String> getter) {
        List<String> values = items.stream().map(getter).toList();
        String base = values.get(0);
        rows.add(new CompareRow(group, label, "", values.stream().map(value -> new CompareCell(
                value == null || value.isBlank() ? "—" : value, Objects.equals(base, value) ? "" : "不同")).toList(),
                values.stream().distinct().count() > 1));
    }

    private void flag(List<CompareRow> rows, String group, String label, List<MouseDevice> items,
                      Function<MouseDevice, Boolean> getter) {
        List<Boolean> values = items.stream().map(getter).toList();
        Boolean base = values.get(0);
        rows.add(new CompareRow(group, label, "", values.stream().map(value -> new CompareCell(
                value == null ? "—" : value ? "是" : "否", Objects.equals(base, value) ? "" : "不同")).toList(),
                values.stream().distinct().count() > 1));
    }

    public static String delta(BigDecimal base, BigDecimal value) {
        if (base == null || value == null || base.signum() == 0) return "";
        BigDecimal result = value.subtract(base).divide(base.abs(), 3, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        return result.signum() == 0 ? "" : (result.signum() > 0 ? "+" : "") + result + "%";
    }

    private BigDecimal number(Integer value) { return value == null ? null : BigDecimal.valueOf(value); }
    private String format(BigDecimal value) { return value == null ? "—" : value.stripTrailingZeros().toPlainString(); }
}
