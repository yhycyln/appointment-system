package com.example.appointment.api;

import com.example.appointment.bot.BotConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bot")
public class BotController {
    private final BotConversationService botConversationService;

    public BotController(BotConversationService botConversationService) {
        this.botConversationService = botConversationService;
    }

    @PostMapping("/messages")
    public BotMessageResponse processMessage(@Valid @RequestBody BotMessageRequest request) {
        BotConversationService.BotAction action = parseAction(request.action());
        BotConversationService.BotReply reply = botConversationService.handleInput(
                request.channel(),
                request.externalUserId(),
                action,
                request.value(),
                request.message());

        List<BotOptionResponse> options = reply.options().stream()
                .map(o -> new BotOptionResponse(
                        o.type().name(),
                        o.label(),
                        o.value()))
                .toList();
        return new BotMessageResponse(
                reply.message(),
                reply.state().name(),
                options);
    }

    private BotConversationService.BotAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        try {
            return BotConversationService.BotAction.valueOf(action);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid action. Allowed values: START, SELECT_EMPLOYEE, SELECT_DAY, SELECT_SLOT, PROVIDE_CUSTOMER_NAME");
        }
    }

    public record BotMessageRequest(
            @NotBlank
            @Pattern(regexp = "TELEGRAM|WHATSAPP", message = "channel must be TELEGRAM or WHATSAPP")
            String channel,
            @NotBlank String externalUserId,
            String action,
            String value,
            String message
    ) {}

    public record BotMessageResponse(
            String message,
            String state,
            List<BotOptionResponse> options
    ) {}

    public record BotOptionResponse(
            String type,
            String label,
            String value
    ) {}
}
