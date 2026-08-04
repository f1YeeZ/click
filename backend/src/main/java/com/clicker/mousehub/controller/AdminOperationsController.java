package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.OperationsDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
    private final AdminOperationsService operations;
    private final FeedbackService feedback;
    private final AdminNotificationService notifications;
    private final SystemSettingService settings;
    public AdminOperationsController(AdminOperationsService operations, FeedbackService feedback,
                                     AdminNotificationService notifications, SystemSettingService settings) {
        this.operations = operations; this.feedback = feedback; this.notifications = notifications; this.settings = settings;
    }

    @GetMapping("/brand-profiles") public List<BrandView> brands() { return operations.brands(); }
    @PostMapping("/brand-profiles") @ResponseStatus(HttpStatus.CREATED)
    public BrandView createBrand(@Valid @RequestBody BrandRequest request) { return operations.saveBrand(null, request); }
    @PutMapping("/brand-profiles/{id}") public BrandView updateBrand(@PathVariable UUID id, @Valid @RequestBody BrandRequest request) { return operations.saveBrand(id, request); }

    @GetMapping("/users/{id}/detail") public UserDetailResponse user(@PathVariable UUID id) { return operations.userDetail(id); }
    @GetMapping("/sessions") public PageResponse<SessionView> sessions(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean activeOnly, @RequestParam(defaultValue = "1") long page) { return operations.sessions(q, activeOnly, page); }
    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID id) { operations.revokeSession(id); }

    @PatchMapping("/mice/{id}/verification") public MouseDevice verify(@PathVariable UUID id, @Valid @RequestBody VerificationRequest request) { return operations.verifyMouse(id, request); }
    @PostMapping("/mice/batch-status") public BatchResult batchMice(@Valid @RequestBody BatchStatusRequest request) { return operations.batchMice(request); }
    @PostMapping("/users/batch-status") public BatchResult batchUsers(@Valid @RequestBody BatchStatusRequest request) { return operations.batchUsers(request); }
    @PostMapping("/reviews/batch-status") public BatchResult batchReviews(@Valid @RequestBody BatchStatusRequest request) { return operations.batchReviews(request); }

    @GetMapping("/analytics") public AnalyticsResponse analytics(@RequestParam(defaultValue = "30") int days) { return operations.analytics(days); }
    @GetMapping("/mice/imports") public PageResponse<ImportJobView> imports(@RequestParam(defaultValue = "1") long page) { return operations.imports(page); }
    @GetMapping("/mice/imports/{checksum}/errors") public ResponseEntity<byte[]> importErrors(@PathVariable String checksum) {
        return download(operations.importError(checksum), "import-errors-" + checksum + ".csv");
    }
    @GetMapping("/exports/{type}") public ResponseEntity<byte[]> export(@PathVariable String type) { return download(operations.export(type), "clicker-" + type + ".csv"); }

    @GetMapping("/reports") public PageResponse<ContentReportView> reports(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") long page) { return feedback.list(q, status, targetType, page); }
    @PatchMapping("/reports/{id}") public ContentReportView report(@PathVariable UUID id, @Valid @RequestBody ReportActionRequest request) { return feedback.update(id, request); }

    @GetMapping("/notifications") public PageResponse<NotificationView> notifications(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") long page) { return notifications.list(unreadOnly, page); }
    @PatchMapping("/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable UUID id) { notifications.read(id); }
    @PostMapping("/notifications/read-all") @ResponseStatus(HttpStatus.NO_CONTENT) public void readAll() { notifications.readAll(); }

    @GetMapping("/settings") public List<SettingView> settings() { return settings.list(); }
    @PutMapping("/settings/{key}") public SettingView setting(@PathVariable String key, @Valid @RequestBody SettingUpdateRequest request) { return settings.update(key, request.value()); }

    private ResponseEntity<byte[]> download(byte[] body, String filename) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(body);
    }
}
