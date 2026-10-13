package com.example.appointment.appointment;

import com.example.appointment.config.BookingProperties;
import com.example.appointment.customer.Customer;
import com.example.appointment.customer.CustomerRepository;
import com.example.appointment.employee.Employee;
import com.example.appointment.employee.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AppointmentService {
    private static final List<AppointmentStatus> RESERVED_STATUSES = List.of(
            AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    private final AppointmentRepository appointmentRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final BookingProperties bookingProperties;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              EmployeeRepository employeeRepository,
                              CustomerRepository customerRepository,
                              BookingProperties bookingProperties) {
        this.appointmentRepository = appointmentRepository;
        this.employeeRepository = employeeRepository;
        this.customerRepository = customerRepository;
        this.bookingProperties = bookingProperties;
    }

    @Transactional
    public Appointment createPending(Long employeeId, String customerName,
                                     String customerPhone,
                                     OffsetDateTime startAt, OffsetDateTime endAt) {
        // Validate time range
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }

        // Validate appointment is not in the past
        OffsetDateTime now = OffsetDateTime.now();
        if (startAt.isBefore(now)) {
            throw new IllegalArgumentException("Appointment start time cannot be in the past");
        }

        // Validate booking horizon (7 days)
        OffsetDateTime maxBookingDate = now.plusDays(bookingProperties.bookingHorizonDays());
        if (startAt.isAfter(maxBookingDate)) {
            throw new IllegalArgumentException(
                    "Appointment cannot be booked more than " + bookingProperties.bookingHorizonDays()
                            + " days in advance");
        }

        // Validate appointment duration
        long durationMinutes = java.time.temporal.ChronoUnit.MINUTES.between(startAt, endAt);
        if (durationMinutes < bookingProperties.slotDurationMinutes()) {
            throw new IllegalArgumentException(
                    "Appointment duration must be at least " + bookingProperties.slotDurationMinutes()
                            + " minutes");
        }

        // Validate employee exists and is active
        Employee employee = employeeRepository.findById(employeeId)
                .filter(Employee::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Active employee not found"));

        // Reuse existing customer or create new
        Customer customer = customerRepository.findByNameAndPhone(customerName, customerPhone)
                .orElseGet(() -> customerRepository.save(new Customer(customerName, customerPhone)));

        boolean overlapping = appointmentRepository
                .existsOverlappingAppointment(employeeId, RESERVED_STATUSES, startAt, endAt);
        if (overlapping) {
            throw new IllegalStateException(
                    "Appointment overlaps with an existing pending/confirmed appointment");
        }

        Appointment saved = appointmentRepository.save(
                new Appointment(employee, customer, startAt, endAt));
        return appointmentRepository.findDetailedById(saved.getId()).orElse(saved);
    }

    @Transactional(readOnly = true)
    public List<Appointment> list(AppointmentStatus status) {
        return status == null
                ? appointmentRepository.findAllByOrderByStartAtDesc()
                : appointmentRepository.findByStatusOrderByStartAtAsc(status);
    }

    @Transactional
    public Appointment approve(Long id) {
        Appointment appointment = appointmentRepository.findDetailedById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found"));
        appointment.approve();
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment reject(Long id) {
        Appointment appointment = appointmentRepository.findDetailedById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found"));
        appointment.reject();
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment cancel(Long id) {
        Appointment appointment = appointmentRepository.findDetailedById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found"));
        appointment.cancel();
        return appointmentRepository.save(appointment);
    }
}
