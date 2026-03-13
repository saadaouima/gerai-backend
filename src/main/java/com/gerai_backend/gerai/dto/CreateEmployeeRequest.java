package com.gerai_backend.gerai.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeRequest {
    private String username;      // goes to Keycloak only
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate hireDate;
    private String jobTitle;
    private BigDecimal salary;
}