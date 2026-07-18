package com.clicker.mousehub.service;

import com.clicker.mousehub.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ImageStorageService {
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
            "jpg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}
    );
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private static final String PUBLIC_PREFIX = "/api/v1/images/";

    private final Path storage;
    private final long maxSizeBytes;

    public ImageStorageService(@Value("${app.images.storage-path:data/mouse-images}") String storagePath,
                               @Value("${app.images.max-size-bytes:5242880}") long maxSizeBytes) {
        this.storage = resolveProjectPath(storagePath);
        this.maxSizeBytes = maxSizeBytes;
        try {
            Files.createDirectories(storage);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建鼠标图片目录", exception);
        }
    }

    public ImageAsset store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("请选择图片文件");
        if (file.getSize() > maxSizeBytes) throw invalid("图片大小不能超过 5 MB");
        String extension = extension(file.getOriginalFilename());
        if (!EXTENSIONS.contains(extension)) throw invalid("仅支持 PNG、JPEG 和 WebP 图片");
        try {
            byte[] bytes = file.getBytes();
            if (!validSignature(extension, bytes)) throw invalid("图片文件格式不正确");
            String normalizedExtension = "jpeg".equals(extension) ? "jpg" : extension;
            String filename = UUID.randomUUID() + "." + normalizedExtension;
            Path target = storage.resolve(filename).normalize();
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            return asset(target);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException("IMAGE_STORE_FAILED", "图片保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public List<ImageAsset> list() {
        try (Stream<Path> files = Files.list(storage)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> EXTENSIONS.contains(extension(path.getFileName().toString())))
                    .sorted(Comparator.comparingLong(this::lastModified).reversed())
                    .map(this::asset).toList();
        } catch (IOException exception) {
            throw new BusinessException("IMAGE_LIST_FAILED", "图片库读取失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Resource load(String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+") || filename.contains("..")) {
            throw notFound();
        }
        Path file = storage.resolve(filename).normalize();
        if (!file.startsWith(storage) || !Files.isRegularFile(file) || !EXTENSIONS.contains(extension(filename))) {
            throw notFound();
        }
        return new FileSystemResource(file);
    }

    public String contentType(String filename) {
        return switch (extension(filename)) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    public Path storagePath() { return storage; }

    private ImageAsset asset(Path file) {
        try {
            String filename = file.getFileName().toString();
            return new ImageAsset(filename, PUBLIC_PREFIX + filename, Files.size(file),
                    OffsetDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneOffset.UTC));
        } catch (IOException exception) {
            throw new BusinessException("IMAGE_READ_FAILED", "图片信息读取失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private long lastModified(Path file) {
        try { return Files.getLastModifiedTime(file).toMillis(); }
        catch (IOException ignored) { return 0; }
    }

    private static boolean validSignature(String extension, byte[] bytes) {
        if ("webp".equals(extension)) {
            return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        }
        byte[] signature = SIGNATURES.get("jpeg".equals(extension) ? "jpg" : extension);
        if (signature == null || bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) if (bytes[index] != signature[index]) return false;
        return true;
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Path resolveProjectPath(String configured) {
        Path path = Path.of(configured);
        if (path.isAbsolute()) return path.normalize();
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int level = 0; level < 4 && current != null; level++, current = current.getParent()) {
            if (Files.isDirectory(current.resolve("backend")) && Files.isDirectory(current.resolve("frontend"))) {
                return current.resolve(path).normalize();
            }
        }
        return Path.of("").toAbsolutePath().resolve(path).normalize();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("INVALID_IMAGE", message, HttpStatus.BAD_REQUEST);
    }
    private static BusinessException notFound() {
        return new BusinessException("IMAGE_NOT_FOUND", "图片不存在", HttpStatus.NOT_FOUND);
    }

    public record ImageAsset(String name, String url, long size, OffsetDateTime updatedAt) {}
}
