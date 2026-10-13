package com.example.appointment.bot;

import com.example.appointment.availability.AvailabilityService;
import com.example.appointment.config.BookingProperties;
import com.example.appointment.employee.Employee;
import com.example.appointment.employee.EmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BotConversationService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy (EEEE)", new Locale("tr"));
    private static final DateTimeFormatter SLOT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SLOT_VALUE_DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_SLOT_OPTIONS = 20;
    private static final String CONTEXT_EMPLOYEE_ID = "employeeId";

    private final TelegramSessionRepository telegramSessionRepository;
    private final AvailabilityService availabilityService;
    private final EmployeeRepository employeeRepository;
    private final BookingProperties bookingProperties;
    private final ObjectMapper objectMapper;

    public BotConversationService(TelegramSessionRepository telegramSessionRepository,
                                  AvailabilityService availabilityService,
                                  EmployeeRepository employeeRepository,
                                  BookingProperties bookingProperties,
                                  ObjectMapper objectMapper) {
        this.telegramSessionRepository = telegramSessionRepository;
        this.availabilityService = availabilityService;
        this.employeeRepository = employeeRepository;
        this.bookingProperties = bookingProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BotReply handleInput(String channel,
                                String externalUserId,
                                BotAction action,
                                String value,
                                String messageText) {
        String userKey = normalizedUserKey(channel, externalUserId);
        TelegramSession session = telegramSessionRepository.findByTelegramUserId(userKey)
                .orElseGet(() -> telegramSessionRepository.save(new TelegramSession(userKey)));

        BotAction effectiveAction = resolveAction(action, messageText);
        return switch (effectiveAction) {
            case START -> handleStart(session);
            case SELECT_EMPLOYEE -> handleEmployeeSelection(session, value);
            case SELECT_DAY -> handleDaySelection(session, value);
            case SELECT_SLOT -> handleSlotSelection(session, value);
        };
    }

    private BotReply handleStart(TelegramSession session) {
        session.setSessionState(BotSessionState.AWAITING_EMPLOYEE_SELECTION);
        clearContext(session);

        List<BotOption> employeeOptions = buildEmployeeOptions();
        if (employeeOptions.isEmpty()) {
            return new BotReply(
                    "Uygun çalışan bulunamadı.",
                    BotSessionState.AWAITING_EMPLOYEE_SELECTION,
                    List.of());
        }
        return new BotReply(
                "Randevu almak istediğiniz çalışanı seçin.",
                BotSessionState.AWAITING_EMPLOYEE_SELECTION,
                employeeOptions);
    }

    private BotReply handleEmployeeSelection(TelegramSession session, String value) {
        Long employeeId = tryParseLong(value);
        if (employeeId == null) {
            return new BotReply(
                    "Çalışan seçimi geçersiz. Lütfen listeden bir çalışan seçin.",
                    BotSessionState.AWAITING_EMPLOYEE_SELECTION,
                    buildEmployeeOptions());
        }

        Optional<Employee> employeeOptional = employeeRepository.findById(employeeId)
                .filter(Employee::isActive);
        if (employeeOptional.isEmpty()) {
            return new BotReply(
                    "Seçilen çalışan aktif değil. Lütfen başka bir çalışan seçin.",
                    BotSessionState.AWAITING_EMPLOYEE_SELECTION,
                    buildEmployeeOptions());
        }

        saveEmployeeToContext(session, employeeId);
        List<BotOption> dayOptions = buildAvailableDayOptionsForEmployee(employeeId);
        if (dayOptions.isEmpty()) {
            return new BotReply(
                    "Bu çalışan için uygun gün bulunamadı. Lütfen başka bir çalışan seçin.",
                    BotSessionState.AWAITING_EMPLOYEE_SELECTION,
                    buildEmployeeOptions());
        }

        session.setSessionState(BotSessionState.AWAITING_DAY_SELECTION);
        return new BotReply(
                employeeOptional.get().getName() + " için randevu günü seçin.",
                BotSessionState.AWAITING_DAY_SELECTION,
                dayOptions);
    }

    private BotReply handleDaySelection(TelegramSession session, String value) {
        Long employeeId = readEmployeeIdFromContext(session);
        if (employeeId == null) {
            return handleStart(session);
        }
        if (value == null || value.isBlank()) {
            return new BotReply(
                    "Gün seçimi geçersiz. Lütfen listeden bir gün seçin.",
                    BotSessionState.AWAITING_DAY_SELECTION,
                    buildAvailableDayOptionsForEmployee(employeeId));
        }
        LocalDate selectedDate = tryParseDate(value);
        if (selectedDate == null) {
            return new BotReply(
                    "Gün formatı geçersiz. Lütfen listeden bir gün seçin.",
                    BotSessionState.AWAITING_DAY_SELECTION,
                    buildAvailableDayOptionsForEmployee(employeeId));
        }

        LocalDate today = LocalDate.now(ZoneId.of(bookingProperties.timezone()));
        LocalDate maxDate = today.plusDays(bookingProperties.bookingHorizonDays());
        if (selectedDate.isBefore(today) || selectedDate.isAfter(maxDate) || selectedDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new BotReply(
                    "Seçilen gün kullanılamaz. Lütfen listeden geçerli bir gün seçin.",
                    BotSessionState.AWAITING_DAY_SELECTION,
                    buildAvailableDayOptionsForEmployee(employeeId));
        }

        List<AvailabilityService.AvailableSlot> slots =
                availabilityService.getAvailableSlotsForEmployee(employeeId, selectedDate);
        if (slots.isEmpty()) {
            return new BotReply(
                    "Bu gün için uygun slot bulunamadı. Lütfen başka bir gün seçin.",
                    BotSessionState.AWAITING_DAY_SELECTION,
                    buildAvailableDayOptionsForEmployee(employeeId));
        }

        List<BotOption> slotOptions = new ArrayList<>();
        for (int i = 0; i < Math.min(slots.size(), MAX_SLOT_OPTIONS); i++) {
            AvailabilityService.AvailableSlot slot = slots.get(i);
            String label = slot.startAt().format(SLOT_TIME_FORMAT)
                    + " - " + slot.endAt().format(SLOT_TIME_FORMAT);
            String slotValue = slot.employeeId()
                    + "|" + slot.startAt().format(SLOT_VALUE_DATE_TIME_FORMAT)
                    + "|" + slot.endAt().format(SLOT_VALUE_DATE_TIME_FORMAT);
            slotOptions.add(new BotOption(BotOptionType.SLOT, label, slotValue));
        }

        session.setSessionState(BotSessionState.AWAITING_SLOT_SELECTION);
        return new BotReply(
                selectedDate.format(DATE_FORMAT) + " için uygun saatleri seçin.",
                BotSessionState.AWAITING_SLOT_SELECTION,
                slotOptions);
    }

    private BotReply handleSlotSelection(TelegramSession session, String value) {
        if (value == null || value.isBlank()) {
            return new BotReply(
                    "Saat seçimi geçersiz. Lütfen listeden bir saat seçin.",
                    BotSessionState.AWAITING_SLOT_SELECTION,
                    List.of());
        }
        session.setSessionState(BotSessionState.START);
        clearContext(session);
        return new BotReply(
                "Saat seçimi alındı: " + value + ". Bir sonraki adımda bu seçimle randevu oluşturabilirsiniz.",
                BotSessionState.START,
                List.of(new BotOption(BotOptionType.EMPLOYEE, "Yeni randevu başlat", "START")));
    }

    private List<BotOption> buildEmployeeOptions() {
        return employeeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(e -> new BotOption(BotOptionType.EMPLOYEE, e.getName(), e.getId().toString()))
                .toList();
    }

    private List<BotOption> buildAvailableDayOptionsForEmployee(Long employeeId) {
        LocalDate today = LocalDate.now(ZoneId.of(bookingProperties.timezone()));
        LocalDate maxDate = today.plusDays(bookingProperties.bookingHorizonDays());
        List<BotOption> options = new ArrayList<>();

        LocalDate cursor = today;
        while (!cursor.isAfter(maxDate)) {
            if (cursor.getDayOfWeek() != DayOfWeek.SUNDAY
                    && !availabilityService.getAvailableSlotsForEmployee(employeeId, cursor).isEmpty()) {
                options.add(new BotOption(
                        BotOptionType.DAY,
                        cursor.format(DAY_LABEL_FORMAT),
                        cursor.format(DATE_FORMAT)));
            }
            cursor = cursor.plusDays(1);
        }
        return options;
    }

    private void saveEmployeeToContext(TelegramSession session, Long employeeId) {
        ObjectNode context = readContextNode(session);
        context.put(CONTEXT_EMPLOYEE_ID, employeeId);
        session.setContext(context.toString());
    }

    private Long readEmployeeIdFromContext(TelegramSession session) {
        JsonNode context = readContextNode(session);
        if (!context.has(CONTEXT_EMPLOYEE_ID)) {
            return null;
        }
        JsonNode employeeIdNode = context.get(CONTEXT_EMPLOYEE_ID);
        if (employeeIdNode.isNumber()) {
            return employeeIdNode.longValue();
        }
        if (employeeIdNode.isTextual()) {
            return tryParseLong(employeeIdNode.asText());
        }
        return null;
    }

    private void clearContext(TelegramSession session) {
        session.setContext("{}");
    }

    private ObjectNode readContextNode(TelegramSession session) {
        try {
            JsonNode parsed = objectMapper.readTree(session.getContext());
            if (parsed instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode();
    }

    private BotAction resolveAction(BotAction action, String messageText) {
        if (action != null) {
            return action;
        }
        String message = messageText == null ? "" : messageText.trim();
        if (message.equalsIgnoreCase("/start")
                || message.equalsIgnoreCase("start")
                || message.equalsIgnoreCase("randevu")
                || message.equalsIgnoreCase("START")) {
            return BotAction.START;
        }
        if (tryParseDate(message) != null) {
            return BotAction.SELECT_DAY;
        }
        return BotAction.START;
    }

    private String normalizedUserKey(String channel, String externalUserId) {
        return channel.toUpperCase(Locale.ROOT) + ":" + externalUserId.trim();
    }

    private LocalDate tryParseDate(String message) {
        try {
            return LocalDate.parse(message, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Long tryParseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public enum BotAction {
        START,
        SELECT_EMPLOYEE,
        SELECT_DAY,
        SELECT_SLOT
    }

    public enum BotOptionType {
        EMPLOYEE,
        DAY,
        SLOT
    }

    public record BotReply(
            String message,
            BotSessionState state,
            List<BotOption> options
    ) {}

    public record BotOption(
            BotOptionType type,
            String label,
            String value
    ) {}
}
