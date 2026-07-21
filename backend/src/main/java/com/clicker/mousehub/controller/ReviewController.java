package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.ReviewDtos.*;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mice/{mouseId}/reviews/mine")
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews) { this.reviews = reviews; }

    @GetMapping public ReviewView mine(@PathVariable UUID mouseId, Authentication auth) {
        ReviewView review = reviews.mine(mouseId, auth.getName());
        if (review == null) throw new BusinessException("REVIEW_NOT_FOUND", "尚未提交评价", HttpStatus.NOT_FOUND);
        return review;
    }
    @PutMapping public ReviewView save(@PathVariable UUID mouseId, Authentication auth, @Valid @RequestBody ReviewRequest request) {
        return reviews.save(mouseId, auth.getName(), request);
    }
    @PutMapping("/base-score") public ReviewView saveBase(@PathVariable UUID mouseId, Authentication auth,
                                                    @Valid @RequestBody BaseScoreRequest request) {
        return reviews.saveBase(mouseId, auth.getName(), request);
    }
    @PutMapping("/grip-scores/{gripStyle}") public ReviewView saveGrip(@PathVariable UUID mouseId, @PathVariable String gripStyle,
                                                                 Authentication auth, @Valid @RequestBody GripScoreRequest request) {
        return reviews.saveGrip(mouseId, auth.getName(), gripStyle, request);
    }
    @PutMapping("/support-positions") public ReviewView saveSupportPositions(@PathVariable UUID mouseId,
                                                                              Authentication auth,
                                                                              @Valid @RequestBody SupportPositionRequest request) {
        return reviews.saveSupportPositions(mouseId, auth.getName(), request);
    }
    @DeleteMapping("/base-score") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBase(@PathVariable UUID mouseId, Authentication auth) { reviews.deleteBase(mouseId, auth.getName()); }
    @DeleteMapping("/grip-scores/{gripStyle}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGrip(@PathVariable UUID mouseId, @PathVariable String gripStyle, Authentication auth) {
        reviews.deleteGrip(mouseId, auth.getName(), gripStyle);
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID mouseId, Authentication auth) { reviews.delete(mouseId, auth.getName()); }
}
