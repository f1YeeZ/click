package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.LeaderboardDtos.LeaderboardResponse;
import com.clicker.mousehub.service.LeaderboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mouse-rankings")
public class MouseRankingController {
    private final LeaderboardService rankings;

    public MouseRankingController(LeaderboardService rankings) { this.rankings = rankings; }

    @GetMapping
    public LeaderboardResponse list(@RequestParam(defaultValue = "overall") String dimension,
                                    @RequestParam(required = false) String gripStyle) {
        return rankings.list(dimension, gripStyle);
    }
}
