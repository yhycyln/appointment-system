package com.example.appointment.appointment;

import com.example.appointment.customer.Customer;
import com.example.appointment.employee.Employee;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected Appointment() {}

    public Appointment(Employee employee, Customer customer,
                       OffsetDateTime startAt, OffsetDateTime endAt) {
        this.employee = employee;
        this.customer = customer;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = AppointmentStatus.PENDING;
    }

    public void approve() {
        if (status != AppointmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending appointments can be approved. Current status: " + status);
        }
        status = AppointmentStatus.CONFIRMED;
        approvedAt = Instant.now();
    }

    public void reject() {
        if (status != AppointmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending appointments can be rejected. Current status: " + status);
        }
        status = AppointmentStatus.REJECTED;
    }

    public void cancel() {
        if (status != AppointmentStatus.PENDING && status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only pending or confirmed appointments can be cancelled. Current status: "
                            + status);
        }
        status = AppointmentStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public Customer getCustomer() { return customer; }
    public OffsetDateTime getStartAt() { return startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
