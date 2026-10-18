package com.example.appointment.bot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BotSessionRepository extends JpaRepository<BotSession, Long> {
    Optional<BotSession> findByExternalUserId(String externalUserId);
}
