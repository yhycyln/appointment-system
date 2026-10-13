package com.example.appointment.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyWorkingHoursRepository extends JpaRepository<WeeklyWorkingHours, Long> {
    List<WeeklyWorkingHours> findByEmployeeIdAndDayOfWeekAndActiveTrue(Long employeeId, short dayOfWeek);
}
