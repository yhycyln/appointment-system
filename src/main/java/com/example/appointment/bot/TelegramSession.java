package com.example.appointment.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "telegram_sessions")
public class TelegramSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false, unique = true, length = 128)
    private String telegramUserId;

    @Column(nullable = false, length = 40)
    private String state = BotSessionState.START.name();

    protected TelegramSession() {}

    public TelegramSession(String telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public Long getId() { return id; }
    public String getTelegramUserId() { return telegramUserId; }
    public String getState() { return state; }
    public BotSessionState getSessionState() {
        try {
            return BotSessionState.valueOf(state);
        } catch (IllegalArgumentException ignored) {
            return BotSessionState.START;
        }
    }

    public void setSessionState(BotSessionState state) {
        this.state = state.name();
    }
}
