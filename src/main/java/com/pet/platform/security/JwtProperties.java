package com.pet.platform.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "pet-service-platform-jwt-secret-key-2026-please-change";
    private int expireDays = 7;
}
