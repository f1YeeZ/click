package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.AdminDtos.*;
import com.clicker.mousehub.dto.MouseDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import com.clicker.mousehub.dto.MouseImportDtos.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final MouseService mice;
    private final AdminService admin;
    private final AuditLogService audit;
    private final MouseImportService imports;
    public AdminController(MouseService mice, AdminService admin, AuditLogService audit, MouseImportService imports) {
        this.mice = mice; this.admin = admin; this.audit = audit; this.imports = imports;
    }

    @GetMapping("/dashboard") public DashboardResponse dashboard() { return admin.dashboard(); }
    @GetMapping("/brands") public java.util.List<String> brands() { return admin.brands(); }
    @GetMapping("/mice") public PageResponse<MouseView> mice(@RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String quality,
                                                               @RequestParam(required = false) String verification,
                                                               @RequestParam(required = false) String workflow,
                                                               @RequestParam(required = false) String assignee,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "12") long pageSize) { return admin.mice(q, status, quality, verification, workflow, assignee, page, pageSize); }
    @PostMapping("/mice")
    public ResponseEntity<MouseView> create(@Valid @RequestBody MouseCreateRequest request) {
        MouseView mouse = mice.create(request);
        return ResponseEntity.created(URI.create("/api/v1/mice/" + mouse.id())).body(mouse);
    }
    @PutMapping("/mice/{id}") public MouseView update(@PathVariable UUID id, @Valid @RequestBody MouseCreateRequest request) { return mice.update(id, request); }
    @PatchMapping("/mice/{id}") public MouseView mouseStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) { return mice.updateStatus(id, request.status(), request.reason()); }
    @GetMapping(value = "/mice/import-template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=clicker-mice-template.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(imports.template());
    }
    @PostMapping(value = "/mice/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview previewImport(@RequestPart("file") MultipartFile file) { return imports.preview(file); }
    @PostMapping(value = "/mice/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult commitImport(@RequestPart("file") MultipartFile file,
                                     @RequestParam String checksum) { return imports.commit(file, checksum); }

    @GetMapping("/users") public PageResponse<AdminUserView> users(@RequestParam(required = false) String q,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(required = false) String role,
                                                                    @RequestParam(defaultValue = "1") long page,
                                                                    @RequestParam(defaultValue = "12") long pageSize) { return admin.users(q, status, role, page, pageSize); }
    @PatchMapping("/users/{id}") public AdminUserView userStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) { return admin.updateUserStatus(id, request); }
    @PatchMapping("/users/{id}/role") public AdminUserView userRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) { return admin.updateUserRole(id, request); }
    @GetMapping("/reviews") public PageResponse<AdminReviewView> reviews(@RequestParam(required = false) String q,
                                                                           @RequestParam(required = false) String status,
                                                                           @RequestParam(defaultValue = "1") long page,
                                                                           @RequestParam(defaultValue = "12") long pageSize) { return admin.reviews(q, status, page, pageSize); }
    @PatchMapping("/reviews/{id}") public AdminReviewView reviewStatus(@PathVariable UUID id, @Valid @RequestBody ModerationRequest request) { return admin.updateReviewStatus(id, request); }
    @GetMapping("/audit-logs") public PageResponse<AuditLogView> auditLogs(@RequestParam(required = false) String q,
                                                                             @RequestParam(required = false) String entityType,
                                                                             @RequestParam(required = false) String action,
                                                                             @RequestParam(required = false) OffsetDateTime from,
                                                                             @RequestParam(required = false) OffsetDateTime to,
                                                                             @RequestParam(defaultValue = "1") long page,
                                                                             @RequestParam(defaultValue = "12") long pageSize) {
        return audit.search(q, entityType, action, from, to, page, pageSize);
    }
    @GetMapping("/audit-logs/{id}") public AuditLogView auditLog(@PathVariable UUID id) { return audit.detail(id); }
}
