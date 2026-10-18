package com.example.appointment.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bot_sessions")
public class BotSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_user_id", nullable = false, unique = true, length = 160)
    private String externalUserId;

    @Column(nullable = false, length = 40)
    private String state = BotSessionState.START.name();

    @Column(nullable = false, columnDefinition = "jsonb")
    private String context = "{}";

    protected BotSession() {}

    public BotSession(String externalUserId) {
        this.externalUserId = externalUserId;
    }

    public String getContext() { return context; }

    public BotSessionState getSessionState() {
        try {
            return BotSessionState.valueOf(state);
        } catch (IllegalArgumentException ignored) {
            return BotSessionState.START;
        }
    }

    public void setSessionState(BotSessionState state) { this.state = state.name(); }
    public void setContext(String context) { this.context = context; }
}
