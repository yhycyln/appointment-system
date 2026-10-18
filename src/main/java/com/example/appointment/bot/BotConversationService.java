package com.example.appointment.bot;

import com.example.appointment.availability.AvailabilityService;
import com.example.appointment.appointment.Appointment;
import com.example.appointment.appointment.AppointmentService;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
    private static final String CONTEXT_START_AT = "startAt";
    private static final String CONTEXT_END_AT = "endAt";
    private static final String CONTEXT_MENU_PAGE = "menuPage";

    private final BotSessionRepository botSessionRepository;
    private final AvailabilityService availabilityService;
    private final EmployeeRepository employeeRepository;
    private final BookingProperties bookingProperties;
    private final ObjectMapper objectMapper;
    private final AppointmentService appointmentService;

    public BotConversationService(BotSessionRepository botSessionRepository,
                                  AvailabilityService availabilityService,
                                  EmployeeRepository employeeRepository,
                                  BookingProperties bookingProperties,
                                  ObjectMapper objectMapper,
                                  AppointmentService appointmentService) {
        this.botSessionRepository = botSessionRepository;
        this.availabilityService = availabilityService;
        this.employeeRepository = employeeRepository;
        this.bookingProperties = bookingProperties;
        this.objectMapper = objectMapper;
        this.appointmentService = appointmentService;
    }

    @Transactional
    public BotReply handleInput(String channel,
                                String externalUserId,
                                BotAction action,
                                String value,
                                String messageText) {
        String userKey = normalizedUserKey(channel, externalUserId);
        BotSession session = botSessionRepository.findByExternalUserId(userKey)
                .orElseGet(() -> botSessionRepository.save(new BotSession(userKey)));

        BotAction effectiveAction = resolveAction(action, messageText);
        return switch (effectiveAction) {
            case START -> handleStart(session);
            case SELECT_EMPLOYEE -> handleEmployeeSelection(session, value);
            case SELECT_DAY -> handleDaySelection(session, value);
            case SELECT_SLOT -> handleSlotSelection(session, value);
            case PROVIDE_CUSTOMER_NAME -> handleCustomerName(session, channel, externalUserId, value);
        };
    }

    @Transactional
    public BotReply handleWhatsAppText(String externalUserId, String text) {
        return handleText("WHATSAPP", externalUserId, text);
    }

    @Transactional
    @Transactional
    public BotReply handleWhatsAppSelection(String externalUserId, String selectionId) {
        return handleSelection("WHATSAPP", externalUserId, selectionId);
    }

    private BotReply handleSelection(String channel, String externalUserId, String selectionId) {
        String userKey = normalizedUserKey(channel, externalUserId);
        BotSession session = botSessionRepository.findByExternalUserId(userKey)
                .orElseGet(() -> botSessionRepository.save(new BotSession(userKey)));
        if ("START".equals(selectionId)) {
            return pageReply(session, handleStart(session));
        }
        if ("CANCEL".equals(selectionId)) {
            session.setSessionState(BotSessionState.START);
            clearContext(session);
            return new BotReply("İşlem iptal edildi.", BotSessionState.START,
                    List.of(new BotOption(BotOptionType.START, "Yeni randevu başlat", "START")));
        }
        if ("MORE".equals(selectionId) || "PREVIOUS".equals(selectionId)) {
            ObjectNode context = readContextNode(session);
            int page = Math.max(0, context.path(CONTEXT_MENU_PAGE).asInt(0));
            context.put(CONTEXT_MENU_PAGE, "MORE".equals(selectionId) ? page + 1 : Math.max(0, page - 1));
            session.setContext(context.toString());
            return pageReply(session, replyForState(session, "Diğer seçenekler:"));
        }

        int separator = selectionId == null ? -1 : selectionId.indexOf(':');
        if (separator < 1) return pageReply(session, replyForState(session, "Lütfen ekrandaki seçeneklerden birini seçin."));
        String type = selectionId.substring(0, separator);
        String value = selectionId.substring(separator + 1);
        BotSessionState state = session.getSessionState();
        List<BotOption> options = optionsForState(session);
        boolean belongsToCurrentMenu = options.stream().anyMatch(option ->
                option.type().name().equals(type) && option.value().equals(value));
        if (!belongsToCurrentMenu) {
            return pageReply(session, replyForState(session, "Bu seçenek artık kullanılamıyor. Lütfen güncel listeden seçim yapın."));
        }
        resetMenuPage(session);
        BotReply reply = switch (state) {
            case AWAITING_EMPLOYEE_SELECTION -> handleEmployeeSelection(session, value);
            case AWAITING_DAY_SELECTION -> handleDaySelection(session, value);
            case AWAITING_SLOT_SELECTION -> handleSlotSelection(session, value);
            default -> handleStart(session);
        };
        return pageReply(session, reply);
    }

    private BotReply handleStart(BotSession session) {
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

    private BotReply handleEmployeeSelection(BotSession session, String value) {
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

    private BotReply handleDaySelection(BotSession session, String value) {
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

        ObjectNode context = readContextNode(session);
        context.put("selectedDate", selectedDate.toString());
        session.setContext(context.toString());
        session.setSessionState(BotSessionState.AWAITING_SLOT_SELECTION);
        return new BotReply(
                selectedDate.format(DATE_FORMAT) + " için uygun saatleri seçin.",
                BotSessionState.AWAITING_SLOT_SELECTION,
                buildAvailableSlotOptions(session));
    }

    private BotReply handleSlotSelection(BotSession session, String value) {
        if (value == null || value.isBlank()) {
            return new BotReply(
                    "Saat seçimi geçersiz. Lütfen listeden bir saat seçin.",
                    BotSessionState.AWAITING_SLOT_SELECTION,
                    List.of());
        }
        String[] pieces = value.split("\\|", -1);
        if (pieces.length != 3) {
            return replyForState(session, "Saat seçimi geçersiz. Lütfen yeniden seçin.");
        }
        Long employeeId = tryParseLong(pieces[0]);
        LocalDateTime startAt = tryParseDateTime(pieces[1]);
        LocalDateTime endAt = tryParseDateTime(pieces[2]);
        if (employeeId == null || !employeeId.equals(readEmployeeIdFromContext(session))
                || startAt == null || endAt == null || !isCurrentAvailableSlot(employeeId, startAt, endAt)) {
            return replyForState(session, "Bu saat artık uygun değil. Lütfen başka bir saat seçin.");
        }
        ObjectNode context = readContextNode(session);
        context.put(CONTEXT_START_AT, startAt.toString());
        context.put(CONTEXT_END_AT, endAt.toString());
        session.setContext(context.toString());
        session.setSessionState(BotSessionState.AWAITING_CUSTOMER_NAME);
        return new BotReply("Seçiminiz alındı. Randevu talebinizi oluşturmak için adınızı ve soyadınızı yazın.",
                BotSessionState.AWAITING_CUSTOMER_NAME, List.of());
    }

    private BotReply handleCustomerName(BotSession session, String channel, String externalUserId, String customerName) {
        if (customerName == null || customerName.isBlank() || customerName.length() > 120) {
            return new BotReply("Lütfen adınızı ve soyadınızı en fazla 120 karakterle yazın.",
                    BotSessionState.AWAITING_CUSTOMER_NAME, List.of());
        }
        JsonNode context = readContextNode(session);
        Long employeeId = readEmployeeIdFromContext(session);
        LocalDateTime startAt = tryParseDateTime(context.path(CONTEXT_START_AT).asText(null));
        LocalDateTime endAt = tryParseDateTime(context.path(CONTEXT_END_AT).asText(null));
        if (employeeId == null || startAt == null || endAt == null) {
            return handleStart(session);
        }
        try {
            Appointment appointment = appointmentService.createPending(employeeId, customerName.trim(),
                    "WHATSAPP".equalsIgnoreCase(channel) ? whatsappPhone(externalUserId) : null,
                    OffsetDateTime.of(startAt, ZoneId.of(bookingProperties.timezone()).getRules().getOffset(startAt)),
                    OffsetDateTime.of(endAt, ZoneId.of(bookingProperties.timezone()).getRules().getOffset(endAt)));
            session.setSessionState(BotSessionState.START);
            clearContext(session);
            return new BotReply("Randevu talebiniz oluşturuldu (#" + appointment.getId()
                    + "). Onay bekliyor. Yeni randevu için aşağıdaki seçeneği kullanabilirsiniz.",
                    BotSessionState.START,
                    List.of(new BotOption(BotOptionType.START, "Yeni randevu başlat", "START")));
        } catch (IllegalStateException ex) {
            session.setSessionState(BotSessionState.START);
            clearContext(session);
            return new BotReply("Üzgünüz, bu saat az önce doldu. Yeni bir talep için RANDEVU yazın.",
                    BotSessionState.START,
                    List.of(new BotOption(BotOptionType.START, "Yeni randevu başlat", "START")));
        }
    }

    private List<BotOption> buildAvailableSlotOptions(BotSession session) {
        JsonNode context = readContextNode(session);
        Long employeeId = readEmployeeIdFromContext(session);
        LocalDate date = tryParseDate(context.path("selectedDate").asText(null));
        if (employeeId == null || date == null) return List.of();
        List<AvailabilityService.AvailableSlot> slots = availabilityService.getAvailableSlotsForEmployee(employeeId, date);
        List<BotOption> options = new ArrayList<>();
        for (int i = 0; i < Math.min(slots.size(), MAX_SLOT_OPTIONS); i++) {
            AvailabilityService.AvailableSlot slot = slots.get(i);
            String label = slot.startAt().format(SLOT_TIME_FORMAT) + " - " + slot.endAt().format(SLOT_TIME_FORMAT);
            String slotValue = slot.employeeId() + "|" + slot.startAt().format(SLOT_VALUE_DATE_TIME_FORMAT)
                    + "|" + slot.endAt().format(SLOT_VALUE_DATE_TIME_FORMAT);
            options.add(new BotOption(BotOptionType.SLOT, label, slotValue));
        }
        return options;
    }

    private boolean isCurrentAvailableSlot(Long employeeId, LocalDateTime startAt, LocalDateTime endAt) {
        return availabilityService.getAvailableSlotsForEmployee(employeeId, startAt.toLocalDate()).stream()
                .anyMatch(slot -> slot.startAt().equals(startAt) && slot.endAt().equals(endAt));
    }

    private BotReply replyForState(BotSession session, String message) {
        return new BotReply(message, session.getSessionState(), optionsForState(session));
    }

    private List<BotOption> optionsForState(BotSession session) {
        return switch (session.getSessionState()) {
            case AWAITING_EMPLOYEE_SELECTION -> buildEmployeeOptions();
            case AWAITING_DAY_SELECTION -> {
                Long employeeId = readEmployeeIdFromContext(session);
                yield employeeId == null ? List.of() : buildAvailableDayOptionsForEmployee(employeeId);
            }
            case AWAITING_SLOT_SELECTION -> buildAvailableSlotOptions(session);
            default -> List.of();
        };
    }

    private BotReply pageReply(BotSession session, BotReply reply) {
        if (reply.options().isEmpty()) return reply;
        int page = Math.max(0, readContextNode(session).path(CONTEXT_MENU_PAGE).asInt(0));
        int start = page == 0 ? 0 : 8 + (page - 1) * 7;
        if (start >= reply.options().size()) {
            page = reply.options().size() <= 9 ? 0 : (reply.options().size() <= 16 ? 1
                    : 1 + (reply.options().size() - 16 + 6) / 7);
            start = page == 0 ? 0 : 8 + (page - 1) * 7;
            ObjectNode context = readContextNode(session);
            context.put(CONTEXT_MENU_PAGE, page);
            session.setContext(context.toString());
        }
        boolean hasPrevious = page > 0;
        boolean hasMore = page == 0
                ? reply.options().size() > 9
                : reply.options().size() > start + 8;
        int capacity = page == 0 ? (hasMore ? 8 : 9) : (hasMore ? 7 : 8);
        List<BotOption> visible = new ArrayList<>();
        int end = Math.min(reply.options().size(), start + capacity);
        visible.addAll(reply.options().subList(start, end));
        if (hasPrevious) visible.add(new BotOption(BotOptionType.PREVIOUS, "Önceki seçenekler", "PREVIOUS"));
        if (end < reply.options().size()) visible.add(new BotOption(BotOptionType.MORE, "Daha fazla seçenek", "MORE"));
        return new BotReply(reply.message(), reply.state(), visible);
    }

    private void resetMenuPage(BotSession session) {
        ObjectNode context = readContextNode(session);
        context.put(CONTEXT_MENU_PAGE, 0);
        session.setContext(context.toString());
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

    private void saveEmployeeToContext(BotSession session, Long employeeId) {
        ObjectNode context = readContextNode(session);
        context.put(CONTEXT_EMPLOYEE_ID, employeeId);
        session.setContext(context.toString());
    }

    private Long readEmployeeIdFromContext(BotSession session) {
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

    private void clearContext(BotSession session) {
        session.setContext("{}");
    }

    private ObjectNode readContextNode(BotSession session) {
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

    private LocalDateTime tryParseDateTime(String value) {
        try {
            return value == null ? null : LocalDateTime.parse(value, SLOT_VALUE_DATE_TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String whatsappPhone(String externalUserId) {
        String digits = externalUserId == null ? "" : externalUserId.replaceAll("\\D", "");
        return digits.isBlank() ? null : "+" + digits;
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
        SELECT_SLOT,
        PROVIDE_CUSTOMER_NAME
    }

    public enum BotOptionType {
        EMPLOYEE,
        DAY,
        SLOT,
        START,
        MORE,
        PREVIOUS
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
