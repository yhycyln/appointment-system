package com.example.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        int slotDurationMinutes,
        int bookingHorizonDays,
        String timezone
) {}
