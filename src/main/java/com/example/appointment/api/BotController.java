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
        BotConversationService.BotReply reply = botConversationService.handleMessage(
                request.channel(), request.externalUserId(), request.message());
        List<BotSlotOptionResponse> slots = reply.slots().stream()
                .map(slot -> new BotSlotOptionResponse(
                        slot.optionNo(),
                        slot.employeeId(),
                        slot.employeeName(),
                        slot.startTime(),
                        slot.endTime()))
                .toList();
        return new BotMessageResponse(reply.message(), slots);
    }

    public record BotMessageRequest(
            @NotBlank
            @Pattern(regexp = "TELEGRAM|WHATSAPP", message = "channel must be TELEGRAM or WHATSAPP")
            String channel,
            @NotBlank String externalUserId,
            @NotBlank String message
    ) {}

    public record BotMessageResponse(
            String message,
            List<BotSlotOptionResponse> slots
    ) {}

    public record BotSlotOptionResponse(
            int optionNo,
            Long employeeId,
            String employeeName,
            String startTime,
            String endTime
    ) {}
}
