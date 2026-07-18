package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.ReviewDtos.*;
import com.clicker.mousehub.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mice/{mouseId}/my-review")
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews) { this.reviews = reviews; }

    @GetMapping public ReviewView mine(@PathVariable UUID mouseId, Authentication auth) { return reviews.mine(mouseId, auth.getName()); }
    @PutMapping public ReviewView save(@PathVariable UUID mouseId, Authentication auth, @Valid @RequestBody ReviewRequest request) {
        return reviews.save(mouseId, auth.getName(), request);
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID mouseId, Authentication auth) { reviews.delete(mouseId, auth.getName()); }
}
