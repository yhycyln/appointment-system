package com.example.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
        String accessToken,
        String phoneNumberId,
        String verifyToken,
        String appSecret,
        String graphApiVersion
) {}
