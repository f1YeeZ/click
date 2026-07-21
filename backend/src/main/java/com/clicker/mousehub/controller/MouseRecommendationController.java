package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.RecommendationDtos.RecommendationResponse;
import com.clicker.mousehub.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/mouse-recommendations")
public class MouseRecommendationController {
    private final RecommendationService recommendations;

    public MouseRecommendationController(RecommendationService recommendations) { this.recommendations = recommendations; }

    @GetMapping
    public RecommendationResponse list(@RequestParam String gripStyle,
                                       @RequestParam String supportPositions) {
        return recommendations.recommend(gripStyle, Arrays.stream(supportPositions.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList());
    }
}
