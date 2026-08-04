package com.clicker.mousehub;

import com.clicker.mousehub.common.BusinessException;
import com.clicker.mousehub.service.ImageStorageService;
import com.clicker.mousehub.service.AuditLogService;
import com.clicker.mousehub.mapper.MouseMapper;
import com.clicker.mousehub.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clicker.mousehub.entity.AuditLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ImageStorageServiceTest {
    @TempDir
    Path storage;

    @Test
    void storesAndListsValidPng() {
        ImageStorageService service = service();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        var stored = service.store(new MockMultipartFile("file", "mouse.png", "image/png", png));

        assertThat(stored.name()).endsWith(".png");
        assertThat(stored.url()).isEqualTo("/api/v1/images/" + stored.name());
        assertThat(service.list()).extracting(ImageStorageService.ImageAsset::name).containsExactly(stored.name());
        assertThat(service.load(stored.name()).exists()).isTrue();
    }

    @Test
    void rejectsContentThatDoesNotMatchImageSignature() {
        ImageStorageService service = service();

        assertThatThrownBy(() -> service.store(
                new MockMultipartFile("file", "mouse.png", "image/png", "not-an-image".getBytes())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片文件格式不正确");
    }

    @Test
    void rejectsPathTraversalWhenLoading() {
        ImageStorageService service = service();

        assertThatThrownBy(() -> service.load("../secret.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片不存在");
    }

    @Test
    void deletesOnlyUnreferencedImages() {
        MouseMapper mice = mock(MouseMapper.class);
        AuditLogMapper auditLogs = mock(AuditLogMapper.class);
        ImageStorageService service = new ImageStorageService(storage.toString(), 1024, mice,
                new AuditLogService(auditLogs, new ObjectMapper()));
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        var stored = service.store(new MockMultipartFile("file", "mouse.png", "image/png", png));

        when(mice.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(stored.name())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍被 1 款鼠标使用");
        assertThat(service.load(stored.name()).exists()).isTrue();

        when(mice.selectCount(any())).thenReturn(0L);
        service.delete(stored.name());
        assertThat(service.list()).isEmpty();
        verify(auditLogs).insert(org.mockito.ArgumentMatchers.<AuditLog>argThat(log -> "IMAGE_DELETE".equals(log.getAction())
                && "IMAGE".equals(log.getEntityType()) && stored.name().equals(log.getEntityId())));
    }

    private ImageStorageService service() {
        return new ImageStorageService(storage.toString(), 1024, mock(MouseMapper.class),
                new AuditLogService(mock(AuditLogMapper.class), new ObjectMapper()));
    }
}
