package com.clicker.mousehub.controller;

import com.clicker.mousehub.dto.AdminDtos.*;
import com.clicker.mousehub.dto.MouseDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final MouseService mice;
    private final AdminService admin;
    public AdminController(MouseService mice, AdminService admin) { this.mice = mice; this.admin = admin; }

    @GetMapping("/dashboard") public DashboardResponse dashboard() { return admin.dashboard(); }
    @GetMapping("/brands") public java.util.List<String> brands() { return admin.brands(); }
    @GetMapping("/mice") public PageResponse<MouseView> mice(@RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "12") long pageSize) { return admin.mice(q, status, page, pageSize); }
    @PostMapping("/mice")
    public ResponseEntity<MouseView> create(@Valid @RequestBody MouseCreateRequest request) {
        MouseView mouse = mice.create(request);
        return ResponseEntity.created(URI.create("/api/v1/mice/" + mouse.id())).body(mouse);
    }
    @PutMapping("/mice/{id}") public MouseView update(@PathVariable UUID id, @Valid @RequestBody MouseCreateRequest request) { return mice.update(id, request); }
    @PatchMapping("/mice/{id}") public MouseView mouseStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) { return mice.updateStatus(id, request.status()); }

    @GetMapping("/users") public PageResponse<AdminUserView> users(@RequestParam(required = false) String q,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "1") long page,
                                                                    @RequestParam(defaultValue = "12") long pageSize) { return admin.users(q, status, page, pageSize); }
    @PatchMapping("/users/{id}") public AdminUserView userStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) { return admin.updateUserStatus(id, request); }
    @GetMapping("/reviews") public PageResponse<AdminReviewView> reviews(@RequestParam(required = false) String status,
                                                                          @RequestParam(defaultValue = "1") long page,
                                                                          @RequestParam(defaultValue = "12") long pageSize) { return admin.reviews(status, page, pageSize); }
    @PatchMapping("/reviews/{id}") public AdminReviewView reviewStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) { return admin.updateReviewStatus(id, request); }
}
