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
    @PutMapping("/base") public ReviewView saveBase(@PathVariable UUID mouseId, Authentication auth,
                                                    @Valid @RequestBody BaseScoreRequest request) {
        return reviews.saveBase(mouseId, auth.getName(), request);
    }
    @PutMapping("/grips/{gripStyle}") public ReviewView saveGrip(@PathVariable UUID mouseId, @PathVariable String gripStyle,
                                                                 Authentication auth, @Valid @RequestBody GripScoreRequest request) {
        return reviews.saveGrip(mouseId, auth.getName(), gripStyle, request);
    }
    @DeleteMapping("/base") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBase(@PathVariable UUID mouseId, Authentication auth) { reviews.deleteBase(mouseId, auth.getName()); }
    @DeleteMapping("/grips/{gripStyle}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGrip(@PathVariable UUID mouseId, @PathVariable String gripStyle, Authentication auth) {
        reviews.deleteGrip(mouseId, auth.getName(), gripStyle);
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID mouseId, Authentication auth) { reviews.delete(mouseId, auth.getName()); }
}
