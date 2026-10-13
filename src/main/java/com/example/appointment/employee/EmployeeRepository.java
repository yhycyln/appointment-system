package com.example.appointment.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findById(Long id);
    List<Employee> findAllByOrderByNameAsc();
    List<Employee> findByActiveTrueOrderByNameAsc();
}
