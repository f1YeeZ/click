package com.clicker.mousehub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.dto.MouseDtos.MouseCreateRequest;
import com.clicker.mousehub.dto.MouseImportDtos.*;
import com.clicker.mousehub.entity.MouseDevice;
import com.clicker.mousehub.entity.MouseImportJob;
import com.clicker.mousehub.mapper.MouseImportJobMapper;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.util.CsvSecurity;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MouseImportService {
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final List<String> HEADERS = List.of(
            "brand", "model", "variant", "slug", "status", "sizeCategory", "lengthMm", "widthMm", "heightMm", "weightG",
            "shapeType", "handCompatibility", "sensorName", "maxDpi", "maxPollingRateHz", "trackingSpeedIps", "accelerationG",
            "buttonCount", "sideButtonCount", "switchName", "encoderName", "connectionModes", "material", "primarySourceUrl",
            "sourceNotes", "materialGeneral", "materialSpecific", "humpPlacement", "frontFlare", "sideCurvature", "thumbRest",
            "ringFingerRest", "sensorType", "adjustableSensorPosition", "sensorPositionX", "sensorPositionY", "sensorPositionX2",
            "sensorPositionY2", "hotSwappableSwitches", "switchType", "switchLifeSpanM", "encoderType", "encoderSteps",
            "purchaseChannels", "imageUrl");

    private final MouseMapper mice;
    private final MouseImportJobMapper jobs;
    private final MouseService mouseService;
    private final AuditLogService audit;
    private final Validator validator;
    private final AdminNotificationService notifications;

    public MouseImportService(MouseMapper mice, MouseImportJobMapper jobs, MouseService mouseService,
                              AuditLogService audit, Validator validator, AdminNotificationService notifications) {
        this.mice = mice;
        this.jobs = jobs;
        this.mouseService = mouseService;
        this.audit = audit;
        this.validator = validator;
        this.notifications = notifications;
    }

    @Transactional
    public ImportPreview preview(MultipartFile file) {
        ParsedBatch batch = analyze(file);
        MouseImportJob existing = jobs.selectById(batch.checksum());
        if (existing == null || !"COMPLETED".equals(existing.getStatus())) {
            MouseImportJob job = existing == null ? new MouseImportJob() : existing;
            job.setChecksum(batch.checksum()); job.setFilename(batch.filename()); job.setActorEmail(audit.currentActor());
            job.setTotalCount(batch.total()); job.setCreatedCount(0); job.setUpdatedCount(0);
            job.setStatus(batch.errors().isEmpty() && !batch.rows().isEmpty() ? "PREVIEW_READY" : "PREVIEW_FAILED");
            job.setErrorReport(errorReport(batch.errors())); job.setCreatedAt(existing == null ? OffsetDateTime.now() : existing.getCreatedAt());
            if (existing == null) jobs.insert(job); else jobs.updateById(job);
            if (!batch.errors().isEmpty()) notifications.create("IMPORT_FAILED", "CSV 预检失败",
                    batch.filename() + " · " + batch.errors().size() + " 个问题", "MOUSE_IMPORT", batch.checksum());
        }
        return batch.preview();
    }

    @Transactional
    public ImportResult commit(MultipartFile file, String expectedChecksum) {
        ParsedBatch batch = analyze(file);
        if (expectedChecksum == null || !expectedChecksum.equals(batch.checksum())) {
            throw new BusinessException("IMPORT_FILE_CHANGED", "导入文件与预检文件不一致，请重新预检", HttpStatus.CONFLICT);
        }
        MouseImportJob existing = jobs.selectById(batch.checksum());
        if (existing != null && "COMPLETED".equals(existing.getStatus())) {
            return new ImportResult(existing.getChecksum(), existing.getCreatedCount(), existing.getUpdatedCount(), true);
        }
        if (!batch.errors().isEmpty() || batch.rows().isEmpty()) {
            throw new BusinessException("IMPORT_NOT_READY", "CSV 仍有错误，不能写入数据库", HttpStatus.BAD_REQUEST);
        }

        int created = 0;
        int updated = 0;
        for (ImportRow row : batch.rows()) {
            if (row.existingId() == null) {
                mouseService.create(row.request());
                created++;
            } else {
                mouseService.update(row.existingId(), row.request());
                updated++;
            }
        }
        MouseImportJob job = existing == null ? new MouseImportJob() : existing;
        job.setChecksum(batch.checksum());
        job.setFilename(batch.filename());
        job.setActorEmail(audit.currentActor());
        job.setCreatedCount(created);
        job.setUpdatedCount(updated);
        job.setTotalCount(batch.total()); job.setStatus("COMPLETED"); job.setErrorReport(null);
        job.setCreatedAt(existing == null ? OffsetDateTime.now() : existing.getCreatedAt()); job.setCompletedAt(OffsetDateTime.now());
        if (existing == null) jobs.insert(job); else jobs.updateById(job);
        ImportResult result = new ImportResult(batch.checksum(), created, updated, false);
        audit.record("MOUSE_CSV_IMPORT", "MOUSE_IMPORT", batch.checksum(),
                "批量导入鼠标：新增 " + created + " 条，更新 " + updated + " 条", null, result, null);
        return result;
    }

    private String errorReport(List<ImportIssue> errors) {
        if (errors == null || errors.isEmpty()) return null;
        StringBuilder csv = new StringBuilder("row,field,value,message\r\n");
        for (ImportIssue issue : errors) csv.append(csvCell(issue.row())).append(',').append(csvCell(issue.field())).append(',')
                .append(csvCell(issue.value())).append(',').append(csvCell(issue.message())).append("\r\n");
        return csv.toString();
    }

    private static String csvCell(Object value) { return CsvSecurity.cell(value); }

    public byte[] template() {
        try {
            StringWriter output = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder()
                    .setHeader(HEADERS.toArray(String[]::new)).build())) {
                printer.printRecord("Example", "Model X", "", "example-model-x", "DRAFT", "MEDIUM", "120", "62", "39", "58",
                        "SYMMETRICAL", "RIGHT", "PAW3395", "26000", "1000", "650", "50", "5", "2", "Optical", "TTC Silver",
                        "wireless_2_4g;wired", "塑料", "https://example.com/model-x", "请替换为真实来源", "塑料", "ABS", "CENTER",
                        "STRAIGHT", "MODERATE", "false", "false", "OPTICAL", "false", "", "", "", "", "false", "OPTICAL",
                        "", "MECHANICAL", "", "官方渠道", "");
            }
            byte[] csv = output.toString().getBytes(StandardCharsets.UTF_8);
            byte[] withBom = new byte[csv.length + 3];
            withBom[0] = (byte) 0xEF; withBom[1] = (byte) 0xBB; withBom[2] = (byte) 0xBF;
            System.arraycopy(csv, 0, withBom, 3, csv.length);
            return withBom;
        } catch (IOException exception) {
            throw new IllegalStateException("无法生成 CSV 模板", exception);
        }
    }

    private ParsedBatch analyze(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("请选择 CSV 文件");
        if (file.getSize() > MAX_FILE_SIZE) throw invalid("CSV 文件不能超过 2 MB");
        String filename = file.getOriginalFilename() == null ? "mice.csv" : file.getOriginalFilename();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) throw invalid("仅支持 CSV 文件");

        try {
            byte[] bytes = file.getBytes();
            String checksum = sha256(bytes);
            String content = decodeUtf8(bytes);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') content = content.substring(1);
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true).setTrim(true).build();
            List<ImportIssue> errors = new ArrayList<>();
            List<ImportRow> rows = new ArrayList<>();
            Set<String> slugs = new HashSet<>();
            Set<String> identities = new HashSet<>();
            int total = 0;
            try (CSVParser parser = format.parse(new StringReader(content))) {
                Set<String> actualHeaders = parser.getHeaderMap().keySet();
                for (String required : List.of("brand", "model", "slug", "sizeCategory", "lengthMm", "widthMm", "heightMm",
                        "weightG", "shapeType", "sensorName", "maxDpi", "maxPollingRateHz", "connectionModes", "primarySourceUrl")) {
                    if (!actualHeaders.contains(required)) errors.add(new ImportIssue(1, required, "", "缺少必需列"));
                }
                if (!errors.isEmpty()) return new ParsedBatch(checksum, filename, 0, List.of(), errors, 0, 0);
                for (CSVRecord record : parser) {
                    total++;
                    if (total > MAX_ROWS) {
                        errors.add(new ImportIssue(record.getRecordNumber() + 1, "file", "", "单次最多导入 1000 行"));
                        break;
                    }
                    long line = record.getRecordNumber() + 1;
                    try {
                        MouseCreateRequest request = request(record);
                        for (ConstraintViolation<MouseCreateRequest> violation : validator.validate(request)) {
                            String field = violation.getPropertyPath().toString();
                            errors.add(new ImportIssue(line, field, value(record, field), violation.getMessage()));
                        }
                        if (!slugs.add(request.slug())) errors.add(new ImportIssue(line, "slug", request.slug(), "文件内 slug 重复"));
                        String identity = request.brand().trim() + "\u0000" + request.model().trim() + "\u0000"
                                + (request.variant() == null ? "" : request.variant().trim());
                        if (!identities.add(identity)) {
                            errors.add(new ImportIssue(line, "brand/model/variant", request.brand() + " " + request.model(), "文件内产品身份重复"));
                        }
                        MouseDevice bySlug = mice.selectOne(new LambdaQueryWrapper<MouseDevice>().eq(MouseDevice::getSlug, request.slug()));
                        MouseDevice byIdentity = mice.selectOne(new LambdaQueryWrapper<MouseDevice>()
                                .eq(MouseDevice::getBrand, request.brand().trim()).eq(MouseDevice::getModel, request.model().trim())
                                .eq(MouseDevice::getVariant, request.variant() == null ? "" : request.variant().trim()));
                        if (byIdentity != null && (bySlug == null || !byIdentity.getId().equals(bySlug.getId()))) {
                            errors.add(new ImportIssue(line, "brand/model/variant", request.brand() + " " + request.model(), "该产品身份已被其他 slug 使用"));
                        }
                        rows.add(new ImportRow(line, request, bySlug == null ? null : bySlug.getId()));
                    } catch (CellException exception) {
                        errors.add(new ImportIssue(line, exception.field, exception.value, exception.getMessage()));
                    }
                }
            }
            Set<Long> invalidRows = new HashSet<>();
            errors.stream().filter(issue -> issue.row() > 1).forEach(issue -> invalidRows.add(issue.row()));
            int valid = Math.max(0, total - invalidRows.size());
            int creates = (int) rows.stream().filter(row -> row.existingId() == null && !invalidRows.contains(row.line())).count();
            int updates = valid - creates;
            return new ParsedBatch(checksum, filename, total, rows, errors, creates, updates);
        } catch (CharacterCodingException exception) {
            throw invalid("CSV 必须使用 UTF-8 编码");
        } catch (IOException exception) {
            throw invalid("CSV 文件读取失败");
        }
    }

    private MouseCreateRequest request(CSVRecord row) {
        return new MouseCreateRequest(
                required(row, "brand"), required(row, "model"), optional(row, "variant"), required(row, "slug"),
                required(row, "sizeCategory"), decimal(row, "lengthMm", true), decimal(row, "widthMm", true),
                decimal(row, "heightMm", true), decimal(row, "weightG", true), required(row, "shapeType"),
                optionalDefault(row, "handCompatibility", "RIGHT"), required(row, "sensorName"), integer(row, "maxDpi", true),
                integer(row, "maxPollingRateHz", true), integer(row, "trackingSpeedIps", false), decimal(row, "accelerationG", false),
                integer(row, "buttonCount", false), integer(row, "sideButtonCount", false), optional(row, "switchName"),
                optional(row, "encoderName"), list(row, "connectionModes"), optional(row, "material"), required(row, "primarySourceUrl"),
                optional(row, "sourceNotes"), optional(row, "materialGeneral"), optional(row, "materialSpecific"), optional(row, "humpPlacement"),
                optional(row, "frontFlare"), optional(row, "sideCurvature"), bool(row, "thumbRest"), bool(row, "ringFingerRest"),
                optional(row, "sensorType"), bool(row, "adjustableSensorPosition"), decimal(row, "sensorPositionX", false),
                decimal(row, "sensorPositionY", false), decimal(row, "sensorPositionX2", false), decimal(row, "sensorPositionY2", false),
                bool(row, "hotSwappableSwitches"), optional(row, "switchType"), integer(row, "switchLifeSpanM", false),
                optional(row, "encoderType"), integer(row, "encoderSteps", false), optional(row, "purchaseChannels"),
                optional(row, "imageUrl"), optionalDefault(row, "status", "DRAFT"));
    }

    private static String required(CSVRecord row, String field) {
        String value = value(row, field);
        if (value.isBlank()) throw new CellException(field, value, "不能为空");
        return value;
    }
    private static String optional(CSVRecord row, String field) { String value = value(row, field); return value.isBlank() ? null : value; }
    private static String optionalDefault(CSVRecord row, String field, String fallback) { String value = value(row, field); return value.isBlank() ? fallback : value; }
    private static String value(CSVRecord row, String field) { return row.isMapped(field) && row.isSet(field) ? row.get(field).trim() : ""; }
    private static BigDecimal decimal(CSVRecord row, String field, boolean required) {
        String value = value(row, field); if (value.isBlank()) { if (required) throw new CellException(field, value, "不能为空"); return null; }
        try { return new BigDecimal(value); } catch (NumberFormatException exception) { throw new CellException(field, value, "必须是数字"); }
    }
    private static Integer integer(CSVRecord row, String field, boolean required) {
        String value = value(row, field); if (value.isBlank()) { if (required) throw new CellException(field, value, "不能为空"); return null; }
        try { return Integer.valueOf(value); } catch (NumberFormatException exception) { throw new CellException(field, value, "必须是整数"); }
    }
    private static Boolean bool(CSVRecord row, String field) {
        String value = value(row, field); if (value.isBlank()) return null;
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "是".equals(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "否".equals(value)) return false;
        throw new CellException(field, value, "只能填写 true/false、1/0 或 是/否");
    }
    private static List<String> list(CSVRecord row, String field) {
        String value = required(row, field);
        return Arrays.stream(value.split("[;,|]")).map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private static BusinessException invalid(String message) { return new BusinessException("INVALID_IMPORT_FILE", message, HttpStatus.BAD_REQUEST); }

    private record ImportRow(long line, MouseCreateRequest request, UUID existingId) {}
    private record ParsedBatch(String checksum, String filename, int total, List<ImportRow> rows,
                               List<ImportIssue> errors, int creates, int updates) {
        ImportPreview preview() {
            int valid = Math.max(0, total - (int) errors.stream().map(ImportIssue::row).filter(row -> row > 1).distinct().count());
            return new ImportPreview(checksum, filename, total, valid, creates, updates, errors, total > 0 && errors.isEmpty());
        }
    }
    private static final class CellException extends RuntimeException {
        private final String field;
        private final String value;
        private CellException(String field, String value, String message) { super(message); this.field = field; this.value = value; }
    }
}
