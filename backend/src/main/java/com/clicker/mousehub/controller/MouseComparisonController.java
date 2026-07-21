package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.MouseDtos.CompareResponse;
import com.clicker.mousehub.service.ComparisonService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mouse-comparisons")
public class MouseComparisonController {
    private final ComparisonService comparisons;

    public MouseComparisonController(ComparisonService comparisons) { this.comparisons = comparisons; }

    @GetMapping
    public CompareResponse get(@RequestParam String mouseIds) {
        List<UUID> ids = Arrays.stream(mouseIds.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .map(UUID::fromString).distinct().limit(4).toList();
        return comparisons.compare(ids);
    }
}
