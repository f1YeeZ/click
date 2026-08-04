package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.MouseDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.entity.Review;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.ReviewMapper;
import com.clicker.mousehub.util.MouseDataQuality;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MouseService {
    private static final Set<String> SIZES = Set.of("FINGERTIP", "EXTRA_SMALL", "SMALL", "MEDIUM", "LARGE");
    private static final Set<String> SHAPES = Set.of("SYMMETRICAL", "ERGONOMIC", "HYBRID");
    private static final Set<String> CONNECTIONS = Set.of("wired", "wireless_2_4g", "bluetooth");
    private final MouseMapper mice;
    private final ReviewMapper reviews;
    private final RealtimeEventService events;
    private final AuditLogService audit;

    public MouseService(MouseMapper mice, ReviewMapper reviews, RealtimeEventService events, AuditLogService audit) {
        this.mice = mice;
        this.reviews = reviews;
        this.events = events;
        this.audit = audit;
    }

    public PageResponse<MouseView> search(String q, String brand, String size, String shape, String connection,
                                          String hand, String humpPlacement, String frontFlare, String sideCurvature,
                                          String thumbRest, String ringFingerRest, String sensorType, String sensorName,
                                          String adjustableSensorPosition, String material, String switchType, String switchName,
                                          String encoderType, String encoderName, String purchaseChannel, String hotSwap,
                                          BigDecimal lengthMin, BigDecimal lengthMax, BigDecimal widthMin, BigDecimal widthMax,
                                          BigDecimal heightMin, BigDecimal heightMax, BigDecimal weightMin, BigDecimal weightMax,
                                          Integer dpiMin, Integer dpiMax, Integer pollingMin, Integer pollingMax,
                                          Integer trackingMin, Integer trackingMax, BigDecimal accelerationMin, BigDecimal accelerationMax,
                                          Integer buttonsMin, Integer buttonsMax, Integer sideButtonsMin, Integer sideButtonsMax,
                                          Integer encoderStepsMin, Integer encoderStepsMax,
                                          String sort, long page, long pageSize) {
        long safeSize = List.of(12L, 24L, 48L).contains(pageSize) ? pageSize : 12;
        LambdaQueryWrapper<MouseDevice> query = new LambdaQueryWrapper<MouseDevice>()
                .eq(MouseDevice::getStatus, "PUBLISHED");
        if (StringUtils.hasText(q)) {
            String pattern = caseInsensitivePattern(q);
            query.apply("LOWER(model) LIKE {0}", pattern);
        }
        query.in(StringUtils.hasText(brand), MouseDevice::getBrand, csv(brand))
                .in(StringUtils.hasText(size), MouseDevice::getSizeCategory, csv(size))
                .in(StringUtils.hasText(shape), MouseDevice::getShapeType, csv(shape))
                .in(StringUtils.hasText(hand), MouseDevice::getHandCompatibility, csv(hand))
                .in(StringUtils.hasText(humpPlacement), MouseDevice::getHumpPlacement, csv(humpPlacement))
                .in(StringUtils.hasText(frontFlare), MouseDevice::getFrontFlare, csv(frontFlare))
                .in(StringUtils.hasText(sideCurvature), MouseDevice::getSideCurvature, csv(sideCurvature))
                .in(StringUtils.hasText(thumbRest), MouseDevice::getThumbRest, booleanCsv(thumbRest))
                .in(StringUtils.hasText(ringFingerRest), MouseDevice::getRingFingerRest, booleanCsv(ringFingerRest))
                .in(StringUtils.hasText(sensorType), MouseDevice::getSensorType, csv(sensorType))
                .apply(StringUtils.hasText(sensorName), "LOWER(sensor_name) LIKE {0}", caseInsensitivePattern(sensorName))
                .in(StringUtils.hasText(adjustableSensorPosition), MouseDevice::getAdjustableSensorPosition, booleanCsv(adjustableSensorPosition))
                .apply(StringUtils.hasText(material), "LOWER(material) LIKE {0}", caseInsensitivePattern(material))
                .in(StringUtils.hasText(switchType), MouseDevice::getSwitchType, csv(switchType))
                .apply(StringUtils.hasText(switchName), "LOWER(switch_name) LIKE {0}", caseInsensitivePattern(switchName))
                .in(StringUtils.hasText(encoderType), MouseDevice::getEncoderType, csv(encoderType))
                .apply(StringUtils.hasText(encoderName), "LOWER(encoder_name) LIKE {0}", caseInsensitivePattern(encoderName))
                .apply(StringUtils.hasText(purchaseChannel), "LOWER(purchase_channels) LIKE {0}", caseInsensitivePattern(purchaseChannel))
                .in(StringUtils.hasText(hotSwap), MouseDevice::getHotSwappableSwitches, booleanCsv(hotSwap))
                .and(StringUtils.hasText(connection), wrapper -> {
                    List<String> values = csv(connection);
                    wrapper.like(MouseDevice::getConnectionModes, values.get(0));
                    values.stream().skip(1).forEach(value -> wrapper.or().like(MouseDevice::getConnectionModes, value));
                })
                .ge(lengthMin != null, MouseDevice::getLengthMm, lengthMin)
                .le(lengthMax != null, MouseDevice::getLengthMm, lengthMax)
                .ge(widthMin != null, MouseDevice::getWidthMm, widthMin)
                .le(widthMax != null, MouseDevice::getWidthMm, widthMax)
                .ge(heightMin != null, MouseDevice::getHeightMm, heightMin)
                .le(heightMax != null, MouseDevice::getHeightMm, heightMax)
                .ge(weightMin != null, MouseDevice::getWeightG, weightMin)
                .le(weightMax != null, MouseDevice::getWeightG, weightMax)
                .ge(dpiMin != null, MouseDevice::getMaxDpi, dpiMin)
                .le(dpiMax != null, MouseDevice::getMaxDpi, dpiMax)
                .ge(pollingMin != null, MouseDevice::getMaxPollingRateHz, pollingMin)
                .le(pollingMax != null, MouseDevice::getMaxPollingRateHz, pollingMax)
                .ge(trackingMin != null, MouseDevice::getTrackingSpeedIps, trackingMin)
                .le(trackingMax != null, MouseDevice::getTrackingSpeedIps, trackingMax)
                .ge(accelerationMin != null, MouseDevice::getAccelerationG, accelerationMin)
                .le(accelerationMax != null, MouseDevice::getAccelerationG, accelerationMax)
                .ge(buttonsMin != null, MouseDevice::getButtonCount, buttonsMin)
                .le(buttonsMax != null, MouseDevice::getButtonCount, buttonsMax)
                .ge(sideButtonsMin != null, MouseDevice::getSideButtonCount, sideButtonsMin)
                .le(sideButtonsMax != null, MouseDevice::getSideButtonCount, sideButtonsMax)
                .ge(encoderStepsMin != null, MouseDevice::getEncoderSteps, encoderStepsMin)
                .le(encoderStepsMax != null, MouseDevice::getEncoderSteps, encoderStepsMax);
        switch (sort == null ? "newest" : sort) {
            case "brand_asc" -> query.orderByAsc(MouseDevice::getBrand, MouseDevice::getModel);
            case "weight_asc" -> query.orderByAsc(MouseDevice::getWeightG, MouseDevice::getId);
            case "weight_desc" -> query.orderByDesc(MouseDevice::getWeightG).orderByAsc(MouseDevice::getId);
            case "rating_desc" -> query.last("""
                    ORDER BY CASE WHEN (SELECT COUNT(*) FROM reviews r
                        WHERE r.mouse_id = mice.id AND r.status = 'ACTIVE' AND r.deleted_at IS NULL
                          AND r.comfort_score IS NOT NULL) >= 5 THEN 0 ELSE 1 END,
                        (SELECT AVG(r.comfort_score) FROM reviews r
                         WHERE r.mouse_id = mice.id AND r.status = 'ACTIVE' AND r.deleted_at IS NULL
                           AND r.comfort_score IS NOT NULL) DESC,
                        (SELECT COUNT(*) FROM reviews r
                         WHERE r.mouse_id = mice.id AND r.status = 'ACTIVE' AND r.deleted_at IS NULL
                           AND r.comfort_score IS NOT NULL) DESC,
                        mice.id ASC
                    """);
            case "review_count_desc" -> query.last("""
                    ORDER BY (SELECT COUNT(*) FROM reviews r
                        WHERE r.mouse_id = mice.id AND r.status = 'ACTIVE' AND r.deleted_at IS NULL
                          AND r.comfort_score IS NOT NULL) DESC,
                        mice.id ASC
                    """);
            default -> query.orderByDesc(MouseDevice::getCreatedAt, MouseDevice::getId);
        }
        Page<MouseDevice> result = mice.selectPage(new Page<>(Math.max(page, 1), safeSize), query);
        return new PageResponse<>(viewsWithRatingStats(result.getRecords()),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    public PageResponse<MouseView> adminSearch(String q, String status, long page, long pageSize) {
        return adminSearch(q, status, null, null, null, null, page, pageSize);
    }

    public PageResponse<MouseView> adminSearch(String q, String status, String quality, String verification,
                                               String workflow, String assignee, long page, long pageSize) {
        long safeSize = List.of(12L, 24L, 48L).contains(pageSize) ? pageSize : 12;
        LambdaQueryWrapper<MouseDevice> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q)) {
            String pattern = caseInsensitivePattern(q);
            query.and(w -> w.apply("LOWER(brand) LIKE {0}", pattern)
                    .or().apply("LOWER(model) LIKE {0}", pattern)
                    .or().apply("LOWER(variant) LIKE {0}", pattern)
                    .or().apply("LOWER(slug) LIKE {0}", pattern)
                    .or().apply("LOWER(aliases) LIKE {0}", pattern)
                    .or().apply("LOWER(sensor_name) LIKE {0}", pattern));
        }
        query.eq(StringUtils.hasText(status), MouseDevice::getStatus, status)
                .eq(StringUtils.hasText(workflow), MouseDevice::getVerificationWorkflowStatus, workflow)
                .eq(StringUtils.hasText(assignee), MouseDevice::getVerificationAssignee, assignee)
                .orderByDesc(MouseDevice::getUpdatedAt);
        if (StringUtils.hasText(quality) || StringUtils.hasText(verification)) {
            List<MouseDevice> filtered = mice.selectList(query).stream()
                    .filter(mouse -> !"INCOMPLETE".equals(quality) || !MouseDataQuality.missingPublicationFields(mouse).isEmpty())
                    .filter(mouse -> !"READY".equals(quality) || MouseDataQuality.missingPublicationFields(mouse).isEmpty())
                    .filter(mouse -> !StringUtils.hasText(verification) || verification.equals(MouseDataQuality.verificationStatus(mouse)))
                    .toList();
            long safePage = Math.max(1, page); int from = (int) Math.min(filtered.size(), (safePage - 1) * safeSize);
            int to = (int) Math.min(filtered.size(), from + safeSize);
            long pages = filtered.isEmpty() ? 0 : (filtered.size() + safeSize - 1) / safeSize;
            return new PageResponse<>(viewsWithRatingStats(filtered.subList(from, to)), new PageResponse.PageMeta(safePage, safeSize, filtered.size(), pages));
        }
        Page<MouseDevice> result = mice.selectPage(new Page<>(Math.max(1, page), safeSize), query);
        return new PageResponse<>(viewsWithRatingStats(result.getRecords()),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    public MouseDevice requirePublished(UUID id) {
        MouseDevice mouse = mice.selectOne(new LambdaQueryWrapper<MouseDevice>()
                .eq(MouseDevice::getId, id).eq(MouseDevice::getStatus, "PUBLISHED"));
        return require(mouse);
    }

    @Transactional
    public MouseView update(UUID id, MouseCreateRequest request) {
        MouseDevice mouse = mice.selectById(id); if (mouse == null) throw new BusinessException("MOUSE_NOT_FOUND", "未找到这款鼠标", HttpStatus.NOT_FOUND);
        MouseView before = MouseView.from(mouse);
        validateOptions(request);
        applyRequest(mouse, request);
        if (StringUtils.hasText(request.status())) mouse.setStatus(request.status());
        validateForStatus(mouse, mouse.getStatus());
        if ("PUBLISHED".equals(mouse.getStatus())) { mouse.setVerifiedAt(OffsetDateTime.now()); mouse.setVerificationWorkflowStatus("DONE"); }
        mouse.setUpdatedAt(OffsetDateTime.now()); mice.updateById(mouse);
        events.publishAfterCommit("mouse.changed", mouse.getId());
        MouseView after = MouseView.from(mouse);
        audit.record("MOUSE_UPDATE", "MOUSE", mouse.getId(), "更新鼠标资料：" + mouse.displayName(), before, after, null);
        return after;
    }

    @Transactional
    public MouseView updateStatus(UUID id, String status) {
        return updateStatus(id, status, null);
    }

    @Transactional
    public MouseView updateStatus(UUID id, String status, String reason) {
        if (!Set.of("PUBLISHED", "DRAFT", "ARCHIVED").contains(status)) {
            throw new BusinessException("INVALID_STATUS", "状态值不符合要求", HttpStatus.BAD_REQUEST);
        }
        MouseDevice mouse = mice.selectById(id);
        if (mouse == null) throw new BusinessException("MOUSE_NOT_FOUND", "未找到这款鼠标", HttpStatus.NOT_FOUND);
        MouseView before = MouseView.from(mouse);
        validateForStatus(mouse, status);
        mouse.setStatus(status);
        if ("PUBLISHED".equals(status)) { mouse.setVerifiedAt(OffsetDateTime.now()); mouse.setVerificationWorkflowStatus("DONE"); }
        mouse.setUpdatedAt(OffsetDateTime.now());
        mice.updateById(mouse);
        events.publishAfterCommit("mouse.changed", mouse.getId());
        MouseView after = MouseView.from(mouse);
        audit.record("MOUSE_STATUS_CHANGE", "MOUSE", mouse.getId(), "鼠标状态变更为 " + status + "：" + mouse.displayName(), before, after, reason);
        return after;
    }

    public List<MouseDevice> publishedInOrder(List<UUID> ids) {
        List<UUID> normalized = ids.stream().distinct().limit(4).toList();
        Map<UUID, MouseDevice> found = new HashMap<>();
        if (!normalized.isEmpty()) mice.selectList(new LambdaQueryWrapper<MouseDevice>()
                .in(MouseDevice::getId, normalized).eq(MouseDevice::getStatus, "PUBLISHED"))
                .forEach(mouse -> found.put(mouse.getId(), mouse));
        return normalized.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    public List<String> brands() { return mice.selectPublishedBrands(); }

    private List<String> csv(String value) {
        return value == null ? List.of() : Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private List<Boolean> booleanCsv(String value) {
        return csv(value).stream().map(Boolean::valueOf).toList();
    }

    private static String caseInsensitivePattern(String value) {
        return "%" + (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    @Transactional
    public MouseView create(MouseCreateRequest request) {
        validateOptions(request);
        OffsetDateTime now = OffsetDateTime.now();
        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID()); mouse.setStatus(StringUtils.hasText(request.status()) ? request.status() : "PUBLISHED");
        mouse.setCreatedAt(now);
        applyRequest(mouse, request);
        validateForStatus(mouse, mouse.getStatus());
        if ("PUBLISHED".equals(mouse.getStatus())) { mouse.setVerifiedAt(now); mouse.setVerificationWorkflowStatus("DONE"); }
        else mouse.setVerificationWorkflowStatus("OPEN");
        mouse.setUpdatedAt(now);
        mice.insert(mouse);
        events.publishAfterCommit("mouse.changed", mouse.getId());
        MouseView created = MouseView.from(mouse);
        audit.record("MOUSE_CREATE", "MOUSE", mouse.getId(), "创建鼠标：" + mouse.displayName(), null, created, null);
        return created;
    }

    private void applyRequest(MouseDevice mouse, MouseCreateRequest request) {
        mouse.setBrand(request.brand().trim()); mouse.setModel(request.model().trim());
        mouse.setVariant(request.variant() == null ? "" : request.variant().trim()); mouse.setSlug(request.slug());
        mouse.setSizeCategory(request.sizeCategory()); mouse.setShapeType(request.shapeType());
        mouse.setHandCompatibility(request.handCompatibility() == null ? "RIGHT" : request.handCompatibility());
        mouse.setLengthMm(request.lengthMm()); mouse.setWidthMm(request.widthMm()); mouse.setHeightMm(request.heightMm());
        mouse.setWeightG(request.weightG()); mouse.setSensorName(request.sensorName()); mouse.setMaxDpi(request.maxDpi());
        mouse.setMaxPollingRateHz(request.maxPollingRateHz()); mouse.setTrackingSpeedIps(request.trackingSpeedIps());
        mouse.setAccelerationG(request.accelerationG()); mouse.setButtonCount(request.buttonCount()); mouse.setSideButtonCount(request.sideButtonCount());
        mouse.setSwitchName(request.switchName()); mouse.setEncoderName(request.encoderName());
        mouse.setConnectionModes(request.connectionModes() == null ? "" : String.join(",", new LinkedHashSet<>(request.connectionModes()))); mouse.setMaterial(request.material());
        mouse.setMaterialGeneral(request.materialGeneral()); mouse.setMaterialSpecific(request.materialSpecific());
        mouse.setHumpPlacement(request.humpPlacement()); mouse.setFrontFlare(request.frontFlare()); mouse.setSideCurvature(request.sideCurvature());
        mouse.setThumbRest(request.thumbRest()); mouse.setRingFingerRest(request.ringFingerRest()); mouse.setSensorType(request.sensorType());
        mouse.setAdjustableSensorPosition(request.adjustableSensorPosition()); mouse.setSensorPositionX(request.sensorPositionX()); mouse.setSensorPositionY(request.sensorPositionY());
        mouse.setSensorPositionX2(request.sensorPositionX2()); mouse.setSensorPositionY2(request.sensorPositionY2()); mouse.setHotSwappableSwitches(request.hotSwappableSwitches());
        mouse.setSwitchType(request.switchType()); mouse.setSwitchLifeSpanM(request.switchLifeSpanM()); mouse.setEncoderType(request.encoderType()); mouse.setEncoderSteps(request.encoderSteps());
        mouse.setPurchaseChannels(request.purchaseChannels()); mouse.setImageUrl(request.imageUrl()); mouse.setPrimarySourceUrl(request.primarySourceUrl()); mouse.setSourceNotes(request.sourceNotes());
    }

    private MouseDevice require(MouseDevice mouse) {
        if (mouse == null) throw new BusinessException("MOUSE_NOT_FOUND", "未找到这款鼠标", HttpStatus.NOT_FOUND);
        return mouse;
    }

    private void validateCode(String value, Set<String> allowed, String label) {
        if (!allowed.contains(value)) throw new BusinessException("INVALID_OPTION", label + "不符合要求", HttpStatus.BAD_REQUEST);
    }

    private void validateOptions(MouseCreateRequest request) {
        if (StringUtils.hasText(request.sizeCategory())) validateCode(request.sizeCategory(), SIZES, "尺寸分类");
        if (StringUtils.hasText(request.shapeType())) validateCode(request.shapeType(), SHAPES, "外形类型");
        if (request.connectionModes() != null && request.connectionModes().stream().anyMatch(mode -> !CONNECTIONS.contains(mode))) {
            throw new BusinessException("INVALID_CONNECTION", "连接模式不符合要求", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateForStatus(MouseDevice mouse, String status) {
        if (!"PUBLISHED".equals(status)) return;
        List<String> missing = MouseDataQuality.missingPublicationFields(mouse);
        if (missing.isEmpty()) return;
        Map<String, String> labels = Map.ofEntries(
                Map.entry("brand", "品牌"), Map.entry("model", "型号"), Map.entry("slug", "Slug"),
                Map.entry("sizeCategory", "尺寸分类"), Map.entry("lengthMm", "长度"), Map.entry("widthMm", "宽度"),
                Map.entry("heightMm", "高度"), Map.entry("weightG", "重量"), Map.entry("shapeType", "外形类型"),
                Map.entry("sensorName", "传感器型号"), Map.entry("maxDpi", "最大 DPI"),
                Map.entry("maxPollingRateHz", "最大回报率"), Map.entry("connectionModes", "连接模式"),
                Map.entry("primarySourceUrl", "有效的数据来源 URL"));
        String fields = missing.stream().map(code -> labels.getOrDefault(code, code)).reduce((a, b) -> a + "、" + b).orElse("");
        throw new BusinessException("MOUSE_PUBLICATION_INCOMPLETE", "发布前请补全：" + fields, HttpStatus.BAD_REQUEST);
    }

    private List<MouseView> viewsWithRatingStats(List<MouseDevice> records) {
        if (records.isEmpty()) return List.of();
        List<UUID> ids = records.stream().map(MouseDevice::getId).toList();
        Map<UUID, List<Review>> byMouse = reviews.selectList(new LambdaQueryWrapper<Review>()
                        .in(Review::getMouseId, ids).eq(Review::getStatus, "ACTIVE")
                        .isNull(Review::getDeletedAt).isNotNull(Review::getComfortScore))
                .stream().collect(java.util.stream.Collectors.groupingBy(Review::getMouseId));
        return records.stream().map(mouse -> {
            List<Review> mouseReviews = byMouse.getOrDefault(mouse.getId(), List.of());
            BigDecimal average = mouseReviews.isEmpty() ? BigDecimal.ZERO : mouseReviews.stream()
                    .map(review -> BigDecimal.valueOf(review.getComfortScore())).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(mouseReviews.size()), 1, java.math.RoundingMode.HALF_UP);
            return MouseView.from(mouse, average, mouseReviews.size());
        }).toList();
    }
}
