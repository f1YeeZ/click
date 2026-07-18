package com.clicker.mousehub.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.clicker.mousehub.entity.*;
import com.clicker.mousehub.mapper.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Configuration
public class SeedDataConfig {
    @Bean
    ApplicationRunner seed(MouseMapper mice, UserMapper users, PasswordEncoder encoder,
                           @Value("${app.seed.enabled:true}") boolean enabled,
                           @Value("${app.seed.admin-email:}") String adminEmail,
                           @Value("${app.seed.admin-password:}") String adminPassword) {
        return args -> {
            if (!enabled) return;
            if (mice.selectCount(null) == 0) samples().forEach(mice::insert);
            if (StringUtils.hasText(adminEmail) && StringUtils.hasText(adminPassword)) {
                String email = UserAccount.normalizeEmail(adminEmail);
                if (users.selectCount(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getEmail, email)) == 0) {
                    OffsetDateTime now = OffsetDateTime.now();
                    UserAccount admin = new UserAccount();
                    admin.setId(UUID.randomUUID()); admin.setEmail(email); admin.setPasswordHash(encoder.encode(adminPassword));
                    admin.setRole("ADMIN"); admin.setStatus("ACTIVE"); admin.setCreatedAt(now); admin.setUpdatedAt(now);
                    users.insert(admin);
                }
            }
        };
    }

    private List<MouseDevice> samples() {
        return List.of(
                mouse("Logitech", "G Pro X Superlight 2", "", "logitech-g-pro-x-superlight-2", "MEDIUM", "SYMMETRICAL", "125", "63.5", "40", "60", "HERO 2", 44000, 8000, "wireless_2_4g,wired", "LIGHTFORCE Hybrid", "TTC White", "塑料"),
                mouse("Razer", "Viper V3 Pro", "", "razer-viper-v3-pro", "MEDIUM", "SYMMETRICAL", "127.1", "63.9", "39.9", "54", "Focus Pro 35K Gen-2", 35000, 8000, "wireless_2_4g,wired", "Razer Optical Gen-3", "光学编码器", "塑料"),
                mouse("Endgame Gear", "OP1 8K", "", "endgame-gear-op1-8k", "SMALL", "SYMMETRICAL", "118.2", "60.5", "37.2", "51.5", "PixArt PAW3395", 26000, 8000, "wired", "Kailh GX", "TTC Silver", "塑料"),
                mouse("ZOWIE", "EC2-DW", "", "zowie-ec2-dw", "MEDIUM", "ERGONOMIC", "123", "65", "42", "61", "PixArt PAW3950", 3200, 4000, "wireless_2_4g,wired", "Huano", "机械编码器", "塑料"),
                mouse("Pulsar", "X2H v3", "Medium", "pulsar-x2h-v3-medium", "MEDIUM", "SYMMETRICAL", "120.4", "65", "39", "54", "Pulsar XS-1", 32000, 8000, "wireless_2_4g,wired", "Pulsar Optical", "Pulsar Blue", "塑料"),
                mouse("LAMZU", "Thorn", "4K", "lamzu-thorn-4k", "MEDIUM", "ERGONOMIC", "119", "65", "42", "52", "PixArt PAW3395", 26000, 4000, "wireless_2_4g,wired", "Optical", "TTC Silver", "塑料"),
                mouse("VAXEE", "NP-01S Wireless", "4K", "vaxee-np-01s-wireless-4k", "SMALL", "HYBRID", "120", "63", "37", "68", "PixArt PAW3395", 3200, 4000, "wireless_2_4g,wired", "Huano", "机械编码器", "塑料"),
                mouse("WLmouse", "Beast X Mini", "", "wlmouse-beast-x-mini", "SMALL", "SYMMETRICAL", "116", "58", "35", "34", "PixArt PAW3395", 26000, 8000, "wireless_2_4g,wired", "Omron Optical", "TTC Dustproof Silver", "镁合金"));
    }

    private MouseDevice mouse(String brand, String model, String variant, String slug, String size, String shape,
                              String length, String width, String height, String weight, String sensor, int dpi,
                              int polling, String modes, String switches, String encoder, String material) {
        OffsetDateTime now = OffsetDateTime.now();
        MouseDevice mouse = new MouseDevice();
        mouse.setId(UUID.randomUUID()); mouse.setBrand(brand); mouse.setModel(model); mouse.setVariant(variant); mouse.setSlug(slug);
        mouse.setStatus("PUBLISHED"); mouse.setSizeCategory(size); mouse.setShapeType(shape); mouse.setHandCompatibility("RIGHT");
        mouse.setLengthMm(new BigDecimal(length)); mouse.setWidthMm(new BigDecimal(width)); mouse.setHeightMm(new BigDecimal(height));
        mouse.setWeightG(new BigDecimal(weight)); mouse.setSensorName(sensor); mouse.setMaxDpi(dpi); mouse.setMaxPollingRateHz(polling);
        mouse.setConnectionModes(modes); mouse.setSwitchName(switches); mouse.setEncoderName(encoder); mouse.setMaterial(material);
        mouse.setPrimarySourceUrl("https://example.com/source/" + slug);
        mouse.setSourceNotes("初始演示数据，上线前请使用厂商来源复核。"); mouse.setVerifiedAt(now); mouse.setCreatedAt(now); mouse.setUpdatedAt(now);
        return mouse;
    }
}
