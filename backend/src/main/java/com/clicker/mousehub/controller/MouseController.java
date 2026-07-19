package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.MouseDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.dto.ReviewDtos.ReviewSummary;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.service.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/mice")
public class MouseController {
    private final MouseService mice;
    private final ReviewService reviews;
    private final ComparisonService comparison;

    public MouseController(MouseService mice, ReviewService reviews, ComparisonService comparison) {
        this.mice = mice; this.reviews = reviews; this.comparison = comparison;
    }

    @GetMapping
    public PageResponse<MouseView> list(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String brand,
                                        @RequestParam(required = false) String size,
                                        @RequestParam(required = false) String shape,
                                        @RequestParam(required = false) String connection,
                                        @RequestParam(required = false) String hand,
                                        @RequestParam(required = false) String humpPlacement,
                                        @RequestParam(required = false) String frontFlare,
                                        @RequestParam(required = false) String sideCurvature,
                                        @RequestParam(required = false) String thumbRest,
                                        @RequestParam(required = false) String ringFingerRest,
                                        @RequestParam(required = false) String sensorType,
                                        @RequestParam(required = false) String sensorName,
                                        @RequestParam(required = false) String adjustableSensorPosition,
                                        @RequestParam(required = false) String material,
                                        @RequestParam(required = false) String switchType,
                                        @RequestParam(required = false) String switchName,
                                        @RequestParam(required = false) String encoderType,
                                        @RequestParam(required = false) String encoderName,
                                        @RequestParam(required = false) String purchaseChannel,
                                        @RequestParam(required = false) String hotSwap,
                                        @RequestParam(required = false) BigDecimal lengthMin,
                                        @RequestParam(required = false) BigDecimal lengthMax,
                                        @RequestParam(required = false) BigDecimal widthMin,
                                        @RequestParam(required = false) BigDecimal widthMax,
                                        @RequestParam(required = false) BigDecimal heightMin,
                                        @RequestParam(required = false) BigDecimal heightMax,
                                        @RequestParam(required = false) BigDecimal weightMin,
                                        @RequestParam(required = false) BigDecimal weightMax,
                                        @RequestParam(required = false) Integer dpiMin,
                                        @RequestParam(required = false) Integer dpiMax,
                                        @RequestParam(required = false) Integer pollingMin,
                                        @RequestParam(required = false) Integer pollingMax,
                                        @RequestParam(required = false) Integer trackingMin,
                                        @RequestParam(required = false) Integer trackingMax,
                                        @RequestParam(required = false) BigDecimal accelerationMin,
                                        @RequestParam(required = false) BigDecimal accelerationMax,
                                        @RequestParam(required = false) Integer buttonsMin,
                                        @RequestParam(required = false) Integer buttonsMax,
                                        @RequestParam(required = false) Integer sideButtonsMin,
                                        @RequestParam(required = false) Integer sideButtonsMax,
                                        @RequestParam(required = false) Integer encoderStepsMin,
                                        @RequestParam(required = false) Integer encoderStepsMax,
                                        @RequestParam(defaultValue = "newest") String sort,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "12") long pageSize) {
        return mice.search(q, brand, size, shape, connection, hand, humpPlacement, frontFlare, sideCurvature,
                thumbRest, ringFingerRest, sensorType, sensorName, adjustableSensorPosition, material, switchType,
                switchName, encoderType, encoderName, purchaseChannel, hotSwap, lengthMin, lengthMax, widthMin, widthMax,
                heightMin, heightMax, weightMin, weightMax, dpiMin, dpiMax, pollingMin, pollingMax, trackingMin, trackingMax,
                accelerationMin, accelerationMax, buttonsMin, buttonsMax, sideButtonsMin, sideButtonsMax,
                encoderStepsMin, encoderStepsMax, sort, page, pageSize);
    }

    @GetMapping("/brands") public List<String> brands() { return mice.brands(); }

    @GetMapping("/compare")
    public CompareResponse compare(@RequestParam String ids) {
        List<UUID> parsed = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isBlank())
                .map(UUID::fromString).distinct().limit(4).toList();
        return comparison.compare(parsed);
    }

    @GetMapping("/{slug}")
    public MouseDetail detail(@PathVariable String slug,
                               @RequestParam(required = false) String gripStyle,
                               @RequestParam(required = false) String handSize) {
        MouseDevice mouse = mice.requirePublishedBySlug(slug);
        return new MouseDetail(MouseView.from(mouse), reviews.summary(mouse.getId(), gripStyle, handSize));
    }

    @GetMapping("/{id}/review-summary")
    public ReviewSummary summary(@PathVariable UUID id,
                                 @RequestParam(required = false) String gripStyle,
                                 @RequestParam(required = false) String handSize) {
        mice.requirePublished(id);
        return reviews.summary(id, gripStyle, handSize);
    }

    public record MouseDetail(MouseView mouse, ReviewSummary reviewSummary) {}
}
