package com.example.appointment.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "breaks")
public class BreakPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_minute", nullable = false)
    private int startMinute;

    @Column(name = "end_minute", nullable = false)
    private int endMinute;

    @Column(nullable = false)
    private boolean active;

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public short getDayOfWeek() { return dayOfWeek; }
    public int getStartMinute() { return startMinute; }
    public int getEndMinute() { return endMinute; }
    public boolean isActive() { return active; }
}
