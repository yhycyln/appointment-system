package com.example.appointment.api;

import com.example.appointment.bot.WhatsAppInboundMessageProcessor;
import com.example.appointment.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {
    private final WhatsAppInboundMessageProcessor messageProcessor;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppInboundMessageProcessor messageProcessor,
                                     WhatsAppProperties properties,
                                     ObjectMapper objectMapper) {
        this.messageProcessor = messageProcessor;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equals(mode) || isBlank(properties.verifyToken())
                || !constantTimeEquals(properties.verifyToken(), verifyToken) || challenge == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestBody byte[] body, HttpServletRequest request) throws Exception {
        if (!hasValidSignature(body, request.getHeader("X-Hub-Signature-256"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode payload = objectMapper.readTree(body);
        JsonNode entries = payload.path("entry");
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) continue;
                for (JsonNode change : changes) {
                    JsonNode messages = change.path("value").path("messages");
                    if (!messages.isArray()) continue; // Delivery/read status notifications need no reply.
                    for (JsonNode message : messages) {
                        String sender = message.path("from").asText("");
                        String messageId = message.path("id").asText("");
                        String text = readMessageText(message);
                        if (!sender.isBlank() && !messageId.isBlank() && text != null) {
                            messageProcessor.process(messageId, sender, text,
                                    "interactive".equals(message.path("type").asText()));
                        }
                    }
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    private String readMessageText(JsonNode message) {
        String type = message.path("type").asText("");
        if ("text".equals(type)) return message.path("text").path("body").asText(null);
        if ("interactive".equals(type)) {
            JsonNode interactive = message.path("interactive");
            String button = interactive.path("button_reply").path("id").asText(null);
            if (button != null) return button;
            return interactive.path("list_reply").path("id").asText(null);
        }
        return null;
    }

    private boolean hasValidSignature(byte[] body, String signatureHeader) throws Exception {
        if (isBlank(properties.appSecret()) || signatureHeader == null
                || !signatureHeader.startsWith("sha256=")) return false;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal(body);
        byte[] received;
        try {
            received = HexFormat.of().parseHex(signatureHeader.substring("sha256=".length()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, received);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
