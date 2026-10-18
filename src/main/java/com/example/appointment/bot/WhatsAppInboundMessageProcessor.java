package com.example.appointment.bot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppInboundMessageProcessor {
    private final JdbcTemplate jdbcTemplate;
    private final BotConversationService conversationService;
    private final WhatsAppCloudApiClient cloudApiClient;

    public WhatsAppInboundMessageProcessor(JdbcTemplate jdbcTemplate,
                                           BotConversationService conversationService,
                                           WhatsAppCloudApiClient cloudApiClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.conversationService = conversationService;
        this.cloudApiClient = cloudApiClient;
    }

    @Transactional
    public void process(String messageId, String sender, String input, boolean interactive) {
        int claimed = jdbcTemplate.update(
                "INSERT INTO processed_whatsapp_messages (message_id) VALUES (?) ON CONFLICT DO NOTHING",
                messageId);
        if (claimed == 0) return;

        BotConversationService.BotReply reply = interactive
                ? conversationService.handleWhatsAppSelection(sender, input)
                : conversationService.handleWhatsAppText(sender, input);
        cloudApiClient.sendReply(sender, reply);
    }
}
