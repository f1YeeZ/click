package com.clicker.mousehub.dto;

import com.clicker.mousehub.entity.MouseDevice;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MouseDtos {
    private MouseDtos() {}

    public record MouseView(
            UUID id, String brand, String model, String variant, String slug, String displayName,
            String status, String sizeCategory, BigDecimal lengthMm, BigDecimal widthMm,
            BigDecimal heightMm, BigDecimal weightG, String shapeType, String handCompatibility,
            String sensorName, Integer maxDpi, Integer maxPollingRateHz, Integer trackingSpeedIps,
            BigDecimal accelerationG, Integer buttonCount, Integer sideButtonCount, String switchName,
            String encoderName, List<String> connectionModes, String material, String primarySourceUrl,
            String sourceNotes, OffsetDateTime verifiedAt, String materialGeneral, String materialSpecific,
            String humpPlacement, String frontFlare, String sideCurvature, Boolean thumbRest, Boolean ringFingerRest,
            String sensorType, Boolean adjustableSensorPosition, BigDecimal sensorPositionX, BigDecimal sensorPositionY,
            BigDecimal sensorPositionX2, BigDecimal sensorPositionY2, Boolean hotSwappableSwitches, String switchType,
            Integer switchLifeSpanM, String encoderType, Integer encoderSteps, String purchaseChannels, String imageUrl) {
        public static MouseView from(MouseDevice mouse) {
            List<String> modes = mouse.getConnectionModes() == null || mouse.getConnectionModes().isBlank()
                    ? List.of() : List.of(mouse.getConnectionModes().split(","));
            return new MouseView(mouse.getId(), mouse.getBrand(), mouse.getModel(), mouse.getVariant(), mouse.getSlug(),
                    mouse.displayName(), mouse.getStatus(), mouse.getSizeCategory(), mouse.getLengthMm(), mouse.getWidthMm(),
                    mouse.getHeightMm(), mouse.getWeightG(), mouse.getShapeType(), mouse.getHandCompatibility(),
                    mouse.getSensorName(), mouse.getMaxDpi(), mouse.getMaxPollingRateHz(), mouse.getTrackingSpeedIps(),
                    mouse.getAccelerationG(), mouse.getButtonCount(), mouse.getSideButtonCount(), mouse.getSwitchName(),
                    mouse.getEncoderName(), modes, mouse.getMaterial(), mouse.getPrimarySourceUrl(), mouse.getSourceNotes(),
                    mouse.getVerifiedAt(), mouse.getMaterialGeneral(), mouse.getMaterialSpecific(), mouse.getHumpPlacement(),
                    mouse.getFrontFlare(), mouse.getSideCurvature(), mouse.getThumbRest(), mouse.getRingFingerRest(),
                    mouse.getSensorType(), mouse.getAdjustableSensorPosition(), mouse.getSensorPositionX(), mouse.getSensorPositionY(),
                    mouse.getSensorPositionX2(), mouse.getSensorPositionY2(), mouse.getHotSwappableSwitches(), mouse.getSwitchType(),
                    mouse.getSwitchLifeSpanM(), mouse.getEncoderType(), mouse.getEncoderSteps(), mouse.getPurchaseChannels(), mouse.getImageUrl());
        }
    }

    public record MouseCreateRequest(
            @NotBlank @Size(max = 80) String brand,
            @NotBlank @Size(max = 120) String model,
            @Size(max = 100) String variant,
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "slug 只能包含小写字母、数字和连字符") String slug,
            @NotBlank String sizeCategory,
            @NotNull @Positive BigDecimal lengthMm,
            @NotNull @Positive BigDecimal widthMm,
            @NotNull @Positive BigDecimal heightMm,
            @NotNull @Positive BigDecimal weightG,
            @NotBlank String shapeType,
            String handCompatibility,
            @NotBlank String sensorName,
            @NotNull @Positive Integer maxDpi,
            @NotNull @Positive Integer maxPollingRateHz,
            Integer trackingSpeedIps,
            BigDecimal accelerationG,
            Integer buttonCount,
            Integer sideButtonCount,
            String switchName,
            String encoderName,
            @NotEmpty List<String> connectionModes,
            String material,
            @NotBlank String primarySourceUrl,
            String sourceNotes,
            String materialGeneral, String materialSpecific, String humpPlacement, String frontFlare, String sideCurvature,
            Boolean thumbRest, Boolean ringFingerRest, String sensorType, Boolean adjustableSensorPosition,
            BigDecimal sensorPositionX, BigDecimal sensorPositionY, BigDecimal sensorPositionX2, BigDecimal sensorPositionY2,
            Boolean hotSwappableSwitches, String switchType, Integer switchLifeSpanM, String encoderType, Integer encoderSteps,
            String purchaseChannels, String imageUrl) {}

    public record CompareResponse(List<MouseView> items, List<CompareRow> rows) {}
    public record CompareRow(String group, String label, String unit, List<CompareCell> cells, boolean different) {}
    public record CompareCell(String value, String delta) {}
}
