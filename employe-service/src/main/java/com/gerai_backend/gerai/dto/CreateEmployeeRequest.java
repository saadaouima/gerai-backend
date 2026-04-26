package com.gerai_backend.gerai.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * Corrections vs l'ancienne version :
 *  - Suppression : jobTitle, salary (n'existent pas dans EMPLOYEES)
 *  - Ajout : deptId, positionId obligatoires (FKs dans la table EMPLOYEES)
 *  - Ajout : managerId, phone, birthDate, nationalId, gender (optionnels)
 *  - username reste Keycloak-only (non stocké dans EMPLOYEES)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEmployeeRequest {

    /** Keycloak uniquement — devient preferred_username dans Keycloak */
    private String username;

    private String firstName;
    private String lastName;
    private String email;
    private LocalDate hireDate;

    /** FK obligatoire → DEPARTMENTS.dept_id */
    private Long deptId;

    /** FK obligatoire → POSITIONS.position_id */
    private Long positionId;

    /** FK optionnel → EMPLOYEES.employee_id (manager direct) */
    private Long managerId;

    /* ── Champs optionnels ─────────────────────────────── */
    private String phone;
    private LocalDate birthDate;
    private String nationalId;
    private String gender;      // M | F | AUTRE
    private String address;
    private String photoUrl;
}