package com.clicker.mousehub.controller;

import com.clicker.mousehub.service.RealtimeEventService;
import com.clicker.mousehub.security.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
public class RealtimeController {
    private final RealtimeEventService events;
    private final ClientAddressResolver addresses;

    public RealtimeController(RealtimeEventService events, ClientAddressResolver addresses) {
        this.events = events; this.addresses = addresses;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0");
        response.setHeader("X-Accel-Buffering", "no");
        return events.connect(addresses.resolve(request));
    }
}
