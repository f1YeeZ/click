package com.clicker.mousehub.controller;

import com.clicker.mousehub.service.ImageStorageService;
import com.clicker.mousehub.service.ImageStorageService.ImageAsset;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@RestController
public class ImageController {
    private final ImageStorageService images;

    public ImageController(ImageStorageService images) { this.images = images; }

    @GetMapping("/api/v1/admin/images")
    public List<ImageAsset> list() { return images.list(); }

    @PostMapping(value = "/api/v1/admin/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageAsset> upload(@RequestPart("file") MultipartFile file) {
        ImageAsset asset = images.store(file);
        return ResponseEntity.created(URI.create(asset.url())).body(asset);
    }

    @DeleteMapping("/api/v1/admin/images/{filename:.+}")
    public ResponseEntity<Void> delete(@PathVariable String filename) {
        images.delete(filename);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/images/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = images.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(images.contentType(filename)))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}
