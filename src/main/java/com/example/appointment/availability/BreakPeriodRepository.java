package com.example.appointment.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BreakPeriodRepository extends JpaRepository<BreakPeriod, Long> {
    List<BreakPeriod> findByEmployeeIdAndDayOfWeekAndActiveTrue(Long employeeId, short dayOfWeek);
}
