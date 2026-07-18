package com.clicker.mousehub.controller;

import com.clicker.mousehub.service.ImageStorageService;
import com.clicker.mousehub.service.ImageStorageService.ImageAsset;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class ImageController {
    private final ImageStorageService images;

    public ImageController(ImageStorageService images) { this.images = images; }

    @GetMapping("/api/v1/admin/images")
    public List<ImageAsset> list() { return images.list(); }

    @PostMapping(value = "/api/v1/admin/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageAsset upload(@RequestPart("file") MultipartFile file) { return images.store(file); }

    @GetMapping("/api/v1/images/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource resource = images.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(images.contentType(filename)))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}
