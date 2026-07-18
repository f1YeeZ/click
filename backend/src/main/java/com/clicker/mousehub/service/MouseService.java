package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.MouseDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.mapper.MouseMapper;
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

    public MouseService(MouseMapper mice) { this.mice = mice; }

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
            String term = q.trim();
            query.and(w -> w.like(MouseDevice::getBrand, term).or().like(MouseDevice::getModel, term)
                    .or().like(MouseDevice::getVariant, term).or().like(MouseDevice::getAliases, term)
                    .or().like(MouseDevice::getSensorName, term));
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
                .like(StringUtils.hasText(sensorName), MouseDevice::getSensorName, sensorName)
                .in(StringUtils.hasText(adjustableSensorPosition), MouseDevice::getAdjustableSensorPosition, booleanCsv(adjustableSensorPosition))
                .like(StringUtils.hasText(material), MouseDevice::getMaterial, material)
                .in(StringUtils.hasText(switchType), MouseDevice::getSwitchType, csv(switchType))
                .like(StringUtils.hasText(switchName), MouseDevice::getSwitchName, switchName)
                .in(StringUtils.hasText(encoderType), MouseDevice::getEncoderType, csv(encoderType))
                .like(StringUtils.hasText(encoderName), MouseDevice::getEncoderName, encoderName)
                .like(StringUtils.hasText(purchaseChannel), MouseDevice::getPurchaseChannels, purchaseChannel)
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
            default -> query.orderByDesc(MouseDevice::getCreatedAt, MouseDevice::getId);
        }
        Page<MouseDevice> result = mice.selectPage(new Page<>(Math.max(page, 1), safeSize), query);
        return new PageResponse<>(result.getRecords().stream().map(MouseView::from).toList(),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    public PageResponse<MouseView> adminSearch(String q, String status, long page, long pageSize) {
        long safeSize = List.of(12L, 24L, 48L).contains(pageSize) ? pageSize : 12;
        LambdaQueryWrapper<MouseDevice> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q)) {
            String term = q.trim();
            query.and(w -> w.like(MouseDevice::getBrand, term).or().like(MouseDevice::getModel, term)
                    .or().like(MouseDevice::getVariant, term).or().like(MouseDevice::getSensorName, term));
        }
        query.eq(StringUtils.hasText(status), MouseDevice::getStatus, status).orderByDesc(MouseDevice::getUpdatedAt);
        Page<MouseDevice> result = mice.selectPage(new Page<>(Math.max(1, page), safeSize), query);
        return new PageResponse<>(result.getRecords().stream().map(MouseView::from).toList(),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    public MouseDevice requirePublishedBySlug(String slug) {
        MouseDevice mouse = mice.selectOne(new LambdaQueryWrapper<MouseDevice>()
                .eq(MouseDevice::getSlug, slug).eq(MouseDevice::getStatus, "PUBLISHED"));
        return require(mouse);
    }

    public MouseDevice requirePublished(UUID id) {
        MouseDevice mouse = mice.selectOne(new LambdaQueryWrapper<MouseDevice>()
                .eq(MouseDevice::getId, id).eq(MouseDevice::getStatus, "PUBLISHED"));
        return require(mouse);
    }

    @Transactional
    public MouseView update(UUID id, MouseCreateRequest request) {
        validateCode(request.sizeCategory(), SIZES, "尺寸分类");
        validateCode(request.shapeType(), SHAPES, "外形类型");
        if (request.connectionModes().stream().anyMatch(mode -> !CONNECTIONS.contains(mode))) throw new BusinessException("INVALID_CONNECTION", "连接模式不符合要求", HttpStatus.BAD_REQUEST);
        MouseDevice mouse = mice.selectById(id); if (mouse == null) throw new BusinessException("MOUSE_NOT_FOUND", "未找到这款鼠标", HttpStatus.NOT_FOUND);
        applyRequest(mouse, request); mouse.setUpdatedAt(OffsetDateTime.now()); mice.updateById(mouse); return MouseView.from(mouse);
    }

    @Transactional
    public void archive(UUID id) {
        MouseDevice mouse = mice.selectById(id); if (mouse == null) throw new BusinessException("MOUSE_NOT_FOUND", "未找到这款鼠标", HttpStatus.NOT_FOUND);
        mouse.setStatus("ARCHIVED"); mouse.setUpdatedAt(OffsetDateTime.now()); mice.updateById(mouse);
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

    @Transactional
    public MouseView create(MouseCreateRequest request) {
        validateCode(request.sizeCategory(), SIZES, "尺寸分类");
        validateCode(request.shapeType(), SHAPES, "外形类型");
        if (request.connectionModes().stream().anyMatch(mode -> !CONNECTIONS.contains(mode))) {
            throw new BusinessException("INVALID_CONNECTION", "连接模式不符合要求", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime now = OffsetDateTime.now();
        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID()); mouse.setStatus("PUBLISHED"); mouse.setCreatedAt(now); mouse.setVerifiedAt(now);
        applyRequest(mouse, request); mouse.setUpdatedAt(now);
        mice.insert(mouse);
        return MouseView.from(mouse);
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
        mouse.setConnectionModes(String.join(",", new LinkedHashSet<>(request.connectionModes()))); mouse.setMaterial(request.material());
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
}
