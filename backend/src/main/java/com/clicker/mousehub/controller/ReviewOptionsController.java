package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.ReviewDtos.ReviewOptions;
import com.clicker.mousehub.service.ReviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/review-options")
public class ReviewOptionsController {
    private final ReviewService reviews;
    public ReviewOptionsController(ReviewService reviews) { this.reviews = reviews; }
    @GetMapping public ReviewOptions options() { return reviews.options(); }
}
