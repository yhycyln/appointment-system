package com.example.appointment.employee;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 64)
    private String timezone = "Europe/Istanbul";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Employee() {}

    public Employee(String name, String timezone) {
        this.name = name;
        this.timezone = timezone == null || timezone.isBlank()
                ? "Europe/Istanbul" : timezone;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public String getTimezone() { return timezone; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setActive(boolean active) { this.active = active; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
