package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.AdminDtos.*;
import com.clicker.mousehub.dto.MouseDtos.MouseView;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AdminService {
    private final MouseMapper mice;
    private final UserMapper users;
    private final ReviewMapper reviews;
    private final MouseService mouseService;

    public AdminService(MouseMapper mice, UserMapper users, ReviewMapper reviews, MouseService mouseService) {
        this.mice = mice; this.users = users; this.reviews = reviews; this.mouseService = mouseService;
    }

    public DashboardResponse dashboard() {
        long total = mice.selectCount(null);
        long published = mice.selectCount(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getStatus, "PUBLISHED"));
        long draft = mice.selectCount(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getStatus, "DRAFT"));
        long userTotal = users.selectCount(null);
        long reviewTotal = reviews.selectCount(null);
        long pending = reviews.selectCount(new LambdaQueryWrapper<Review>().eq(Review::getStatus, "PENDING"));
        List<AdminUserView> recentUsers = users.selectList(new LambdaQueryWrapper<UserAccount>().orderByDesc(UserAccount::getCreatedAt).last("LIMIT 5"))
                .stream().map(AdminUserView::from).toList();
        List<Review> recentReviewEntities = reviews.selectList(new LambdaQueryWrapper<Review>().orderByDesc(Review::getCreatedAt).last("LIMIT 5"));
        List<AdminReviewView> recentReviews = recentReviewEntities.stream().map(this::reviewView).toList();
        List<MouseView> recentMice = mice.selectList(new LambdaQueryWrapper<MouseDevice>().orderByDesc(MouseDevice::getCreatedAt).last("LIMIT 5"))
                .stream().map(MouseView::from).toList();
        return new DashboardResponse(total, published, draft, userTotal, reviewTotal, pending, recentUsers, recentReviews, recentMice);
    }

    public PageResponse<MouseView> mice(String q, String status, long page, long pageSize) {
        return mouseService.adminSearch(q, status, page, pageSize);
    }

    public List<String> brands() {
        return mice.selectAllBrands();
    }

    public PageResponse<AdminUserView> users(String q, String status, long page, long pageSize) {
        Page<UserAccount> result = users.selectPage(new Page<>(Math.max(1, page), safeSize(pageSize)), new LambdaQueryWrapper<UserAccount>()
                .and(hasText(q), w -> w.like(UserAccount::getEmail, q.trim()))
                .eq(hasText(status), UserAccount::getStatus, status)
                .orderByDesc(UserAccount::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(AdminUserView::from).toList(), meta(result));
    }

    public PageResponse<AdminReviewView> reviews(String status, long page, long pageSize) {
        Page<Review> result = reviews.selectPage(new Page<>(Math.max(1, page), safeSize(pageSize)), new LambdaQueryWrapper<Review>()
                .eq(hasText(status), Review::getStatus, status).orderByDesc(Review::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(this::reviewView).toList(), meta(result));
    }

    @Transactional
    public AdminUserView updateUserStatus(UUID id, StatusRequest request) {
        if (!Set.of("ACTIVE", "DISABLED").contains(request.status())) throw invalidStatus();
        UserAccount user = users.selectById(id); if (user == null) throw notFound("用户");
        user.setStatus(request.status()); user.setUpdatedAt(OffsetDateTime.now()); users.updateById(user); return AdminUserView.from(user);
    }

    @Transactional
    public AdminReviewView updateReviewStatus(UUID id, StatusRequest request) {
        if (!Set.of("ACTIVE", "DISABLED", "PENDING").contains(request.status())) throw invalidStatus();
        Review review = reviews.selectById(id); if (review == null) throw notFound("评价");
        review.setStatus(request.status()); review.setDeletedAt("DISABLED".equals(request.status()) ? OffsetDateTime.now() : null);
        review.setUpdatedAt(OffsetDateTime.now()); reviews.updateById(review); return reviewView(review);
    }

    private AdminReviewView reviewView(Review review) {
        UserAccount user = users.selectById(review.getUserId()); MouseDevice mouse = mice.selectById(review.getMouseId());
        return AdminReviewView.from(review, user == null ? "—" : user.getEmail(), mouse == null ? "—" : mouse.displayName());
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static long safeSize(long size) { return Set.of(12L, 24L, 48L).contains(size) ? size : 12; }
    private static <T> PageResponse.PageMeta meta(Page<T> page) { return new PageResponse.PageMeta(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()); }
    private static BusinessException notFound(String name) { return new BusinessException("NOT_FOUND", name + "不存在", HttpStatus.NOT_FOUND); }
    private static BusinessException invalidStatus() { return new BusinessException("INVALID_STATUS", "状态值不符合要求", HttpStatus.BAD_REQUEST); }
}
