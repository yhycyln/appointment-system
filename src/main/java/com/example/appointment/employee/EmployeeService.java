package com.example.appointment.employee;

import com.example.appointment.customer.Customer;
import com.example.appointment.customer.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {
    private static final String DEFAULT_TIMEZONE = "Europe/Istanbul";

    private final EmployeeRepository repository;
    private final CustomerRepository customerRepository;

    public EmployeeService(EmployeeRepository repository, CustomerRepository customerRepository) {
        this.repository = repository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Employee create(String name) {
        return repository.save(new Employee(name, DEFAULT_TIMEZONE));
    }

    @Transactional(readOnly = true)
    public List<Employee> list(boolean activeOnly) {
        return activeOnly
                ? repository.findByActiveTrueOrderByNameAsc()
                : repository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Employee deactivate(Long id) {
        Employee employee = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        employee.setActive(false);
        return repository.save(employee);
    }

    public List<Customer> listCustomers() {
        return customerRepository.findAll();
    }
}
