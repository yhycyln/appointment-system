package com.example.appointment.bot;

import com.example.appointment.availability.AvailabilityService;
import com.example.appointment.config.BookingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BotConversationService {
    private static final DateTimeFormatter BOT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter SLOT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TelegramSessionRepository telegramSessionRepository;
    private final AvailabilityService availabilityService;
    private final BookingProperties bookingProperties;

    public BotConversationService(TelegramSessionRepository telegramSessionRepository,
                                  AvailabilityService availabilityService,
                                  BookingProperties bookingProperties) {
        this.telegramSessionRepository = telegramSessionRepository;
        this.availabilityService = availabilityService;
        this.bookingProperties = bookingProperties;
    }

    @Transactional
    public BotReply handleMessage(String channel, String externalUserId, String messageText) {
        String userKey = normalizedUserKey(channel, externalUserId);
        String message = messageText == null ? "" : messageText.trim();

        TelegramSession session = telegramSessionRepository.findByTelegramUserId(userKey)
                .orElseGet(() -> telegramSessionRepository.save(new TelegramSession(userKey)));

        if (message.equalsIgnoreCase("/start")
                || message.equalsIgnoreCase("start")
                || message.toLowerCase(Locale.ROOT).contains("randevu")) {
            session.setSessionState(BotSessionState.AWAITING_DATE);
            return new BotReply(
                    "Randevu almak istediğiniz günü yazın (format: yyyy-MM-dd).",
                    List.of());
        }

        LocalDate selectedDate = tryParseDate(message);
        if (selectedDate == null) {
            if (session.getSessionState() != BotSessionState.AWAITING_DATE) {
                session.setSessionState(BotSessionState.AWAITING_DATE);
                return new BotReply(
                        "Merhaba 👋 Randevu almak istediğiniz günü yazın (format: yyyy-MM-dd).",
                        List.of());
            }
            session.setSessionState(BotSessionState.AWAITING_DATE);
            return new BotReply("Tarih formatı geçersiz. Lütfen yyyy-MM-dd formatında yazın.",
                    List.of());
        }

        LocalDate today = LocalDate.now(ZoneId.of(bookingProperties.timezone()));
        LocalDate maxDate = today.plusDays(bookingProperties.bookingHorizonDays());
        if (selectedDate.isBefore(today)) {
            return new BotReply("Geçmiş tarih için randevu oluşturulamaz. Lütfen bugünü veya ileri bir tarihi seçin.",
                    List.of());
        }
        if (selectedDate.isAfter(maxDate)) {
            return new BotReply(
                    "En fazla " + bookingProperties.bookingHorizonDays()
                            + " gün sonrası için seçim yapabilirsiniz.",
                    List.of());
        }

        List<AvailabilityService.AvailableSlot> slots = availabilityService.getAvailableSlots(selectedDate);
        if (slots.isEmpty()) {
            return new BotReply("Bu gün için uygun slot bulunamadı. Lütfen başka bir gün yazın.",
                    List.of());
        }

        List<BotSlotOption> options = new ArrayList<>();
        for (int i = 0; i < Math.min(slots.size(), 20); i++) {
            AvailabilityService.AvailableSlot slot = slots.get(i);
            options.add(new BotSlotOption(
                    i + 1,
                    slot.employeeId(),
                    slot.employeeName(),
                    slot.startAt().format(SLOT_TIME_FORMAT),
                    slot.endAt().format(SLOT_TIME_FORMAT)));
        }
        session.setSessionState(BotSessionState.START);
        return new BotReply(
                "Uygun slotlar aşağıdadır. Seçmek istediğiniz slot numarasını gönderin.",
                options);
    }

    private String normalizedUserKey(String channel, String externalUserId) {
        return channel.toUpperCase(Locale.ROOT) + ":" + externalUserId.trim();
    }

    private LocalDate tryParseDate(String message) {
        try {
            return LocalDate.parse(message, BOT_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public record BotReply(String message, List<BotSlotOption> slots) {}

    public record BotSlotOption(
            int optionNo,
            Long employeeId,
            String employeeName,
            String startTime,
            String endTime
    ) {}
}
