package com.clicker.mousehub.controller;
import com.clicker.mousehub.dto.OperationsDtos.PublicSettings;
import com.clicker.mousehub.service.SystemSettingService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/config")
public class PublicConfigController {
    private final SystemSettingService settings;
    public PublicConfigController(SystemSettingService settings) { this.settings = settings; }
    @GetMapping public PublicSettings get() { return settings.publicSettings(); }
}
