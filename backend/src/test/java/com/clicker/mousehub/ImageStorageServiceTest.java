package com.clicker.mousehub;

import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageStorageServiceTest {
    @TempDir
    Path storage;

    @Test
    void storesAndListsValidPng() {
        ImageStorageService service = new ImageStorageService(storage.toString(), 1024);
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        var stored = service.store(new MockMultipartFile("file", "mouse.png", "image/png", png));

        assertThat(stored.name()).endsWith(".png");
        assertThat(stored.url()).isEqualTo("/api/v1/images/" + stored.name());
        assertThat(service.list()).extracting(ImageStorageService.ImageAsset::name).containsExactly(stored.name());
        assertThat(service.load(stored.name()).exists()).isTrue();
    }

    @Test
    void rejectsContentThatDoesNotMatchImageSignature() {
        ImageStorageService service = new ImageStorageService(storage.toString(), 1024);

        assertThatThrownBy(() -> service.store(
                new MockMultipartFile("file", "mouse.png", "image/png", "not-an-image".getBytes())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片文件格式不正确");
    }

    @Test
    void rejectsPathTraversalWhenLoading() {
        ImageStorageService service = new ImageStorageService(storage.toString(), 1024);

        assertThatThrownBy(() -> service.load("../secret.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片不存在");
    }
}
