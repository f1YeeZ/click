package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.AdminDtos.AdminUserView;
import com.clicker.mousehub.dto.OperationsDtos.*;
import com.clicker.mousehub.dto.PageResponse;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminOperationsService {
    private final BrandProfileMapper brands;
    private final MouseMapper mice;
    private final UserMapper users;
    private final ReviewMapper reviews;
    private final AuditLogMapper auditLogs;
    private final AuthSessionMapper authSessions;
    private final MouseImportJobMapper importJobs;
    private final MouseService mouseService;
    private final AdminService adminService;
    private final AuditLogService audit;
    private final FeedbackService feedback;
    private final AdminNotificationService notifications;

    public AdminOperationsService(BrandProfileMapper brands, MouseMapper mice, UserMapper users, ReviewMapper reviews,
                                  AuditLogMapper auditLogs, AuthSessionMapper authSessions, MouseImportJobMapper importJobs,
                                  MouseService mouseService, AdminService adminService, AuditLogService audit,
                                  FeedbackService feedback, AdminNotificationService notifications) {
        this.brands = brands; this.mice = mice; this.users = users; this.reviews = reviews; this.auditLogs = auditLogs;
        this.authSessions = authSessions; this.importJobs = importJobs; this.mouseService = mouseService;
        this.adminService = adminService; this.audit = audit; this.feedback = feedback; this.notifications = notifications;
    }

    @Transactional
    public List<BrandView> brands() {
        OffsetDateTime now = OffsetDateTime.now();
        Set<String> existing = brands.selectList(null).stream().map(BrandProfile::getName).collect(Collectors.toSet());
        for (String name : mice.selectAllBrands()) if (!existing.contains(name)) {
            BrandProfile brand = new BrandProfile(); brand.setId(UUID.randomUUID()); brand.setName(name); brand.setStatus("ACTIVE");
            brand.setCreatedAt(now); brand.setUpdatedAt(now); brands.insert(brand);
        }
        Map<String, Long> counts = mice.selectList(null).stream().collect(Collectors.groupingBy(MouseDevice::getBrand, Collectors.counting()));
        return brands.selectList(new LambdaQueryWrapper<BrandProfile>().orderByAsc(BrandProfile::getName)).stream()
                .map(value -> BrandView.from(value, counts.getOrDefault(value.getName(), 0L))).toList();
    }

    @Transactional
    public BrandView saveBrand(UUID id, BrandRequest request) {
        BrandProfile brand = id == null ? null : brands.selectById(id);
        BrandView before = brand == null ? null : BrandView.from(brand, countBrand(brand.getName()));
        String oldName = brand == null ? null : brand.getName(); OffsetDateTime now = OffsetDateTime.now();
        if (brand == null) { brand = new BrandProfile(); brand.setId(UUID.randomUUID()); brand.setCreatedAt(now); }
        brand.setName(request.name().trim()); brand.setOfficialUrl(blank(request.officialUrl())); brand.setLogoUrl(blank(request.logoUrl()));
        brand.setAliases(blank(request.aliases())); brand.setNotes(blank(request.notes()));
        brand.setStatus(request.status() == null ? "ACTIVE" : request.status()); brand.setUpdatedAt(now);
        if (id == null) brands.insert(brand); else brands.updateById(brand);
        if (oldName != null && !oldName.equals(brand.getName())) {
            for (MouseDevice mouse : mice.selectList(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getBrand, oldName))) {
                mouse.setBrand(brand.getName()); mouse.setUpdatedAt(now); mice.updateById(mouse);
            }
        }
        BrandView after = BrandView.from(brand, countBrand(brand.getName()));
        audit.record(id == null ? "BRAND_CREATE" : "BRAND_UPDATE", "BRAND", brand.getId(), "维护品牌资料：" + brand.getName(), before, after, null);
        return after;
    }

    public UserDetailResponse userDetail(UUID id) {
        UserAccount user = users.selectById(id); if (user == null) throw notFound("用户");
        List<SessionView> sessions = sessionViews(id);
        long reviewCount = reviews.selectCount(new LambdaQueryWrapper<Review>().eq(Review::getUserId, id));
        return new UserDetailResponse(AdminUserView.from(user), reviewCount, sessions.stream().filter(SessionView::active).count(),
                sessions, audit.search(user.getEmail(), null, null, null, null, 1, 12).items());
    }

    public PageResponse<SessionView> sessions(String q, boolean activeOnly, long page) {
        Set<UUID> ids = new HashSet<>();
        if (q != null && !q.isBlank()) users.selectList(new LambdaQueryWrapper<UserAccount>().like(UserAccount::getEmail, q.trim())).forEach(u -> ids.add(u.getId()));
        LambdaQueryWrapper<AuthSession> query = new LambdaQueryWrapper<AuthSession>()
                .in(q != null && !q.isBlank(), AuthSession::getUserId, ids.isEmpty() ? List.of(new UUID(0, 0)) : ids)
                .isNull(activeOnly, AuthSession::getRevokedAt).gt(activeOnly, AuthSession::getExpiresAt, OffsetDateTime.now())
                .orderByDesc(AuthSession::getLastUsedAt).orderByDesc(AuthSession::getCreatedAt);
        Page<AuthSession> result = authSessions.selectPage(new Page<>(Math.max(1, page), 20), query);
        return new PageResponse<>(result.getRecords().stream().map(this::sessionView).toList(),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }

    @Transactional
    public void revokeSession(UUID id) {
        AuthSession session = authSessions.selectById(id); if (session == null) throw notFound("会话");
        if (session.getRevokedAt() == null) { session.setRevokedAt(OffsetDateTime.now()); authSessions.updateById(session); }
        audit.record("SESSION_REVOKE", "SESSION", id, "管理员撤销登录会话", null, sessionView(session), null);
    }

    @Transactional
    public MouseDevice verifyMouse(UUID id, VerificationRequest request) {
        MouseDevice mouse = mice.selectById(id); if (mouse == null) throw notFound("鼠标");
        var before = com.clicker.mousehub.dto.MouseDtos.MouseView.from(mouse);
        mouse.setVerificationWorkflowStatus(request.status()); mouse.setVerificationAssignee(blank(request.assigneeEmail()));
        mouse.setVerificationNote(blank(request.note())); mouse.setVerificationDueAt(request.dueAt());
        if ("DONE".equals(request.status())) { mouse.setVerifiedAt(OffsetDateTime.now()); mouse.setVerificationAssignee(audit.currentActor()); }
        mouse.setUpdatedAt(OffsetDateTime.now()); mice.updateById(mouse);
        audit.record("MOUSE_VERIFICATION", "MOUSE", id, "数据复核状态变更为 " + request.status(), before,
                com.clicker.mousehub.dto.MouseDtos.MouseView.from(mouse), request.note());
        return mouse;
    }

    public BatchResult batchMice(BatchStatusRequest request) { return batch(request, id -> { mouseService.updateStatus(id, request.status(), request.reason()); return null; }); }
    public BatchResult batchUsers(BatchStatusRequest request) { return batch(request, id -> { adminService.updateUserStatus(id, new com.clicker.mousehub.dto.AdminDtos.StatusRequest(request.status(), request.reason())); return null; }); }
    public BatchResult batchReviews(BatchStatusRequest request) { return batch(request, id -> { adminService.updateReviewStatus(id, new com.clicker.mousehub.dto.AdminDtos.ModerationRequest(request.status(), request.reason())); return null; }); }
    private BatchResult batch(BatchStatusRequest request, Function<UUID, Void> action) {
        int changed = 0; List<String> errors = new ArrayList<>();
        for (UUID id : request.ids().stream().distinct().toList()) try { action.apply(id); changed++; }
        catch (RuntimeException exception) { errors.add(id + "：" + exception.getMessage()); }
        return new BatchResult(request.ids().size(), changed, errors);
    }

    public AnalyticsResponse analytics(int requestedDays) {
        int days = Set.of(7, 14, 30, 90).contains(requestedDays) ? requestedDays : 30;
        LocalDate start = LocalDate.now().minusDays(days - 1L); OffsetDateTime from = start.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        Map<LocalDate, Long> userData = countByDate(users.selectList(new LambdaQueryWrapper<UserAccount>().ge(UserAccount::getCreatedAt, from)), UserAccount::getCreatedAt);
        Map<LocalDate, Long> mouseData = countByDate(mice.selectList(new LambdaQueryWrapper<MouseDevice>().ge(MouseDevice::getCreatedAt, from)), MouseDevice::getCreatedAt);
        Map<LocalDate, Long> reviewData = countByDate(reviews.selectList(new LambdaQueryWrapper<Review>().ge(Review::getCreatedAt, from)), Review::getCreatedAt);
        Map<LocalDate, Long> actionData = countByDate(auditLogs.selectList(new LambdaQueryWrapper<AuditLog>().ge(AuditLog::getCreatedAt, from)), AuditLog::getCreatedAt);
        List<AnalyticsPoint> points = start.datesUntil(LocalDate.now().plusDays(1)).map(date -> new AnalyticsPoint(date,
                userData.getOrDefault(date, 0L), mouseData.getOrDefault(date, 0L), reviewData.getOrDefault(date, 0L), actionData.getOrDefault(date, 0L))).toList();
        long activeSessions = authSessions.selectCount(new LambdaQueryWrapper<AuthSession>().isNull(AuthSession::getRevokedAt).gt(AuthSession::getExpiresAt, OffsetDateTime.now()));
        long stale = mice.selectList(null).stream().filter(mouse -> "STALE".equals(com.clicker.mousehub.util.MouseDataQuality.verificationStatus(mouse))).count();
        return new AnalyticsResponse(days, points, feedback.openCount(), notifications.unreadCount(), activeSessions, stale);
    }

    public PageResponse<ImportJobView> imports(long page) {
        Page<MouseImportJob> result = importJobs.selectPage(new Page<>(Math.max(1, page), 12),
                new LambdaQueryWrapper<MouseImportJob>().orderByDesc(MouseImportJob::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(ImportJobView::from).toList(),
                new PageResponse.PageMeta(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }
    public byte[] importError(String checksum) {
        MouseImportJob job = importJobs.selectById(checksum);
        if (job == null || job.getErrorReport() == null) throw notFound("错误报告");
        return utf8Csv(job.getErrorReport());
    }

    public byte[] export(String type) {
        String csv = switch (type) {
            case "mice" -> "id,brand,model,variant,status,quality,verification,updatedAt\r\n" + mice.selectList(null).stream()
                    .map(m -> row(m.getId(), m.getBrand(), m.getModel(), m.getVariant(), m.getStatus(), com.clicker.mousehub.util.MouseDataQuality.qualityPercent(m), com.clicker.mousehub.util.MouseDataQuality.verificationStatus(m), m.getUpdatedAt())).collect(Collectors.joining("\r\n"));
            case "users" -> "id,email,role,status,createdAt,updatedAt\r\n" + users.selectList(null).stream()
                    .map(u -> row(u.getId(), u.getEmail(), u.getRole(), u.getStatus(), u.getCreatedAt(), u.getUpdatedAt())).collect(Collectors.joining("\r\n"));
            case "reviews" -> "id,userId,mouseId,status,comfortAverage,createdAt\r\n" + reviews.selectList(null).stream()
                    .map(r -> row(r.getId(), r.getUserId(), r.getMouseId(), r.getStatus(), r.getComfortScore(), r.getCreatedAt())).collect(Collectors.joining("\r\n"));
            case "audit" -> "createdAt,actor,action,entityType,entityId,summary,reason\r\n" + auditLogs.selectList(new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getCreatedAt)).stream()
                    .map(a -> row(a.getCreatedAt(), a.getActorEmail(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getSummary(), a.getReason())).collect(Collectors.joining("\r\n"));
            default -> throw new BusinessException("INVALID_EXPORT", "不支持的导出类型", HttpStatus.BAD_REQUEST);
        };
        return utf8Csv(csv);
    }

    private List<SessionView> sessionViews(UUID userId) { return authSessions.selectList(new LambdaQueryWrapper<AuthSession>().eq(AuthSession::getUserId, userId).orderByDesc(AuthSession::getCreatedAt)).stream().map(this::sessionView).toList(); }
    private SessionView sessionView(AuthSession session) { UserAccount user = users.selectById(session.getUserId()); OffsetDateTime now = OffsetDateTime.now(); return new SessionView(session.getId(), session.getUserId(), user == null ? "—" : user.getEmail(), session.getIpAddress(), session.getUserAgent(), session.getRevokedAt() == null && session.getExpiresAt().isAfter(now), session.getCreatedAt(), session.getLastUsedAt(), session.getExpiresAt(), session.getRevokedAt()); }
    private long countBrand(String name) { return mice.selectCount(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getBrand, name)); }
    private static <T> Map<LocalDate, Long> countByDate(List<T> values, Function<T, OffsetDateTime> date) { return values.stream().collect(Collectors.groupingBy(value -> date.apply(value).toLocalDate(), Collectors.counting())); }
    private static byte[] utf8Csv(String value) { byte[] content = value.getBytes(StandardCharsets.UTF_8); byte[] output = new byte[content.length + 3]; output[0] = (byte) 0xEF; output[1] = (byte) 0xBB; output[2] = (byte) 0xBF; System.arraycopy(content, 0, output, 3, content.length); return output; }
    private static String row(Object... cells) { return Arrays.stream(cells).map(AdminOperationsService::cell).collect(Collectors.joining(",")); }
    private static String cell(Object value) { String text = value == null ? "" : value.toString(); return "\"" + text.replace("\"", "\"\"") + "\""; }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static BusinessException notFound(String label) { return new BusinessException("NOT_FOUND", label + "不存在", HttpStatus.NOT_FOUND); }
}
