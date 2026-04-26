package com.gerai_backend.gerai.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Corrections vs l'ancienne version :
 *  - id UUID → Long (PK Oracle IDENTITY)
 *  - Suppression : jobTitle, salary
 *  - Ajout : employeeCode, deptId, positionId, status
 *  - temporaryPassword reste présent (affiché une seule fois, jamais stocké)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeResponse {

    /** employee_id Oracle (NUMBER IDENTITY) */
    private Long id;

    private String employeeCode;  // matricule généré
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate hireDate;

    private Long deptId;
    private Long positionId;
    private Long managerId;

    private String status;        // ACTIF par défaut
    private String keycloakUserId;

    /** Mot de passe temporaire — affiché une seule fois, jamais stocké en DB */
    private String temporaryPassword;

    private LocalDateTime createdAt;
}