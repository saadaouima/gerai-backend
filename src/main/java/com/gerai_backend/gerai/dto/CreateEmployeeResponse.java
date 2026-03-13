package com.gerai_backend.gerai.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate hireDate;
    private String jobTitle;
    private BigDecimal salary;
    private String keycloakUserId;
    private String temporaryPassword;   // ← shown ONCE, never stored
}
