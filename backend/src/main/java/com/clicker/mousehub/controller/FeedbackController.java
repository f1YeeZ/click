package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.OperationsDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FeedbackController {
    private final FeedbackService feedback;
    public FeedbackController(FeedbackService feedback) { this.feedback = feedback; }
    @GetMapping("/mice/{mouseId}/reviews")
    public PageResponse<PublicReviewView> reviews(@PathVariable UUID mouseId, @RequestParam(defaultValue = "1") long page) {
        return feedback.publicReviews(mouseId, page);
    }
    @PostMapping("/reports") @ResponseStatus(HttpStatus.CREATED)
    public ContentReportView report(Authentication authentication, @Valid @RequestBody ReportCreateRequest request) {
        return feedback.create(authentication.getName(), request);
    }
    @PostMapping("/feedback") @ResponseStatus(HttpStatus.CREATED)
    public ContentReportView generalFeedback(Authentication authentication,
                                             @Valid @RequestBody GeneralFeedbackRequest request) {
        return feedback.createGeneral(authentication == null ? null : authentication.getName(), request);
    }
}
