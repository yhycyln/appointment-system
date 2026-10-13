package com.example.appointment.api;

import com.example.appointment.appointment.*;
import com.example.appointment.customer.Customer;
import com.example.appointment.employee.Employee;
import com.example.appointment.employee.EmployeeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final EmployeeService employeeService;
    private final AppointmentService appointmentService;

    public AdminController(EmployeeService employeeService,
                           AppointmentService appointmentService) {
        this.employeeService = employeeService;
        this.appointmentService = appointmentService;
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        Employee employee = employeeService.create(request.name());
        return EmployeeResponse.from(employee);
    }

    @GetMapping("/employees")
    public List<EmployeeResponse> listEmployees(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return employeeService.list(activeOnly).stream().map(EmployeeResponse::from).toList();
    }

    @GetMapping("/customers")
    public List<CustomerResponse> listCustomers() {
        return employeeService.listCustomers().stream().map(CustomerResponse::from).toList();
    }

    @PostMapping("/employees/{id}/deactivate")
    public EmployeeResponse deactivateEmployee(@PathVariable Long id) {
        return EmployeeResponse.from(employeeService.deactivate(id));
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createPending(@Valid @RequestBody CreateAppointmentRequest request) {
        return AppointmentResponse.from(appointmentService.createPending(
                request.employeeId(), request.customerName(), request.customerPhone(),
                request.startAt(), request.endAt()));
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> listAppointments(
            @RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.list(status).stream().map(AppointmentResponse::from).toList();
    }

    @PostMapping("/appointments/{id}/approve")
    public AppointmentResponse approve(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.approve(id));
    }

    @PostMapping("/appointments/{id}/reject")
    public AppointmentResponse reject(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.reject(id));
    }

    @PostMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.cancel(id));
    }

    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 120) String name) {}

    public record CreateAppointmentRequest(
            @NotNull Long employeeId,
            @NotBlank @Size(max = 120) String customerName,
            @Size(max = 32) String customerPhone,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startAt,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endAt) {}

    public record EmployeeResponse(Long id, String name, boolean active, String timezone) {
        static EmployeeResponse from(Employee e) {
            return new EmployeeResponse(e.getId(), e.getName(), e.isActive(), e.getTimezone());
        }
    }

    public record CustomerResponse(Long id, String name, String phone) {
        static CustomerResponse from(Object c) {
            // Assuming c is an instance of a Customer class with getId(), getName(), and getPhone() methods
            return new CustomerResponse(
                    ((Customer) c).getId(),
                    ((Customer) c).getName(),
                    ((Customer) c).getPhone());
        }
    }

    public record AppointmentResponse(
            Long id, Long employeeId, String employeeName,
            Long customerId, String customerName, String customerPhone,
            OffsetDateTime startAt, OffsetDateTime endAt, AppointmentStatus status) {
        static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(
                    a.getId(), a.getEmployee().getId(), a.getEmployee().getName(),
                    a.getCustomer().getId(), a.getCustomer().getName(),
                    a.getCustomer().getPhone(), a.getStartAt(), a.getEndAt(), a.getStatus());
        }
    }
}
