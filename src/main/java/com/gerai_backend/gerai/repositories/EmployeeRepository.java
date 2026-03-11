package com.gerai_backend.gerai.repositories;

import com.gerai_backend.gerai.models.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Custom query method to find employee by email
    Optional<Employee> findByEmail(String email);
}

