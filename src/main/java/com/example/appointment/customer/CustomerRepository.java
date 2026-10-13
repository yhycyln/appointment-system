package com.example.appointment.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("SELECT c FROM Customer c WHERE c.name = :name AND c.phone = :phone")
    Optional<Customer> findByNameAndPhone(String name, String phone);

}
