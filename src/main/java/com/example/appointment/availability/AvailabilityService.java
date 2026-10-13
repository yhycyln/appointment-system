package com.example.appointment.availability;

import com.example.appointment.appointment.Appointment;
import com.example.appointment.appointment.AppointmentRepository;
import com.example.appointment.appointment.AppointmentStatus;
import com.example.appointment.config.BookingProperties;
import com.example.appointment.employee.Employee;
import com.example.appointment.employee.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AvailabilityService {
    private static final List<AppointmentStatus> RESERVED_STATUSES = List.of(
            AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
    private static final int DEFAULT_WORK_START_MINUTE = 10 * 60; // 10:00
    private static final int DEFAULT_WORK_END_MINUTE = 24 * 60;   // 24:00

    private final EmployeeRepository employeeRepository;
    private final BreakPeriodRepository breakPeriodRepository;
    private final BlockedPeriodRepository blockedPeriodRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotCalculator slotCalculator;
    private final BookingProperties bookingProperties;

    public AvailabilityService(EmployeeRepository employeeRepository,
                               BreakPeriodRepository breakPeriodRepository,
                               BlockedPeriodRepository blockedPeriodRepository,
                               AppointmentRepository appointmentRepository,
                               SlotCalculator slotCalculator,
                               BookingProperties bookingProperties) {
        this.employeeRepository = employeeRepository;
        this.breakPeriodRepository = breakPeriodRepository;
        this.blockedPeriodRepository = blockedPeriodRepository;
        this.appointmentRepository = appointmentRepository;
        this.slotCalculator = slotCalculator;
        this.bookingProperties = bookingProperties;
    }

    public List<AvailableSlot> getAvailableSlots(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return List.of();
        }

        ZoneId zone = ZoneId.of(bookingProperties.timezone());
        OffsetDateTime dayStart = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime now = OffsetDateTime.now(zone);

        List<AvailableSlot> availableSlots = new ArrayList<>();
        short dayOfWeek = (short) date.getDayOfWeek().getValue();

        for (Employee employee : employeeRepository.findByActiveTrueOrderByNameAsc()) {
            List<BreakPeriod> breaks = breakPeriodRepository
                    .findByEmployeeIdAndDayOfWeekAndActiveTrue(employee.getId(), dayOfWeek);
            List<SlotCalculator.MinuteRange> breakRanges = breaks.stream()
                    .map(b -> new SlotCalculator.MinuteRange(b.getStartMinute(), b.getEndMinute()))
                    .toList();

            List<BlockedPeriod> blocked = blockedPeriodRepository.findOverlappingByEmployeeId(
                    employee.getId(), dayStart, dayEnd);
            List<Appointment> reserved = appointmentRepository.findReservedOverlappingAppointments(
                    employee.getId(), RESERVED_STATUSES, dayStart, dayEnd);

            List<SlotCalculator.Slot> slots = slotCalculator.generate(
                    date,
                    DEFAULT_WORK_START_MINUTE,
                    DEFAULT_WORK_END_MINUTE,
                    breakRanges,
                    bookingProperties.slotDurationMinutes());

            for (SlotCalculator.Slot slot : slots) {
                OffsetDateTime slotStart = slot.start().atZone(zone).toOffsetDateTime();
                OffsetDateTime slotEnd = slot.end().atZone(zone).toOffsetDateTime();
                if (slotStart.isBefore(now)) {
                    continue;
                }

                boolean conflictsBlocked = blocked.stream()
                        .anyMatch(b -> overlaps(slotStart, slotEnd, b.getStartAt(), b.getEndAt()));
                if (conflictsBlocked) {
                    continue;
                }

                boolean conflictsAppointment = reserved.stream()
                        .anyMatch(a -> overlaps(slotStart, slotEnd, a.getStartAt(), a.getEndAt()));
                if (conflictsAppointment) {
                    continue;
                }

                availableSlots.add(new AvailableSlot(
                        employee.getId(),
                        employee.getName(),
                        slot.start(),
                        slot.end()));
            }
        }

        availableSlots.sort(Comparator
                .comparing(AvailableSlot::startAt)
                .thenComparing(AvailableSlot::employeeName));
        return availableSlots;
    }

    private boolean overlaps(OffsetDateTime startA, OffsetDateTime endA,
                             OffsetDateTime startB, OffsetDateTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    public record AvailableSlot(
            Long employeeId,
            String employeeName,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {}
}
