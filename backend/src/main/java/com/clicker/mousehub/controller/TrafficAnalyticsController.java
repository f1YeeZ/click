package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.OperationsDtos.PageViewRequest;
import com.clicker.mousehub.service.TrafficAnalyticsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class TrafficAnalyticsController {
    private final TrafficAnalyticsService traffic;

    public TrafficAnalyticsController(TrafficAnalyticsService traffic) {
        this.traffic = traffic;
    }

    @PostMapping("/page-views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pageView(@Valid @RequestBody PageViewRequest request,
                         @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        traffic.record(request, userAgent);
    }
}
