package com.example.appointment.bot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramSessionRepository extends JpaRepository<TelegramSession, Long> {
    Optional<TelegramSession> findByTelegramUserId(String telegramUserId);
}
