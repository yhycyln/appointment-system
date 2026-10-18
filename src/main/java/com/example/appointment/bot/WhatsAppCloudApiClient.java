package com.example.appointment.bot;

import com.example.appointment.config.WhatsAppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WhatsAppCloudApiClient {
    private final RestClient restClient;
    private final WhatsAppProperties properties;

    public WhatsAppCloudApiClient(RestClient.Builder builder, WhatsAppProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public void sendReply(String recipient, BotConversationService.BotReply reply) {
        if (isBlank(properties.accessToken()) || isBlank(properties.phoneNumberId())) {
            throw new IllegalStateException("WhatsApp Cloud API is not configured");
        }
        if (reply.state() == BotSessionState.AWAITING_CUSTOMER_NAME) {
            sendInteractive(recipient, Map.of(
                    "type", "button",
                    "body", Map.of("text", reply.message()),
                    "action", Map.of("buttons", List.of(Map.of(
                            "type", "reply",
                            "reply", Map.of("id", "CANCEL", "title", "İptal / başa dön"))))));
            return;
        }
        if (reply.options().isEmpty() && reply.state() == BotSessionState.START) {
            sendText(recipient, reply.message());
            return;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BotConversationService.BotOption option : reply.options()) {
            String id = switch (option.type()) {
                case START, MORE, PREVIOUS -> option.value();
                default -> option.type().name() + ":" + option.value();
            };
            rows.add(row(id, option.label()));
        }
        rows.add(row("CANCEL", "İptal / başa dön"));
        sendInteractive(recipient, Map.of(
                "type", "list",
                "body", Map.of("text", reply.message()),
                "action", Map.of(
                        "button", "Seçenekleri aç",
                        "sections", List.of(Map.of("title", "Randevu seçenekleri", "rows", rows)))));
    }

    private void sendText(String recipient, String text) {
        sendPayload(recipient, Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipient,
                "type", "text",
                "text", Map.of("preview_url", false, "body", text)));
    }

    private void sendInteractive(String recipient, Map<String, Object> interactive) {
        sendPayload(recipient, Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipient,
                "type", "interactive",
                "interactive", interactive));
    }

    private void sendPayload(String recipient, Map<String, Object> payload) {
        restClient.post()
                .uri("https://graph.facebook.com/{version}/{phoneNumberId}/messages",
                        properties.graphApiVersion(), properties.phoneNumberId())
                .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> row(String id, String label) {
        String title = truncate(label, 24);
        String description = label.codePointCount(0, label.length()) > 24 ? truncate(label, 72) : "";
        if (description.isBlank()) return Map.of("id", id, "title", title);
        return Map.of("id", id, "title", title, "description", description);
    }

    private String truncate(String value, int maxCodePoints) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maxCodePoints) return value;
        int end = value.offsetByCodePoints(0, maxCodePoints - 1);
        return value.substring(0, end) + "…";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
