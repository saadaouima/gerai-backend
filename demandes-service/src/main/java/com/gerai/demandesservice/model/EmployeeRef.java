package com.gerai.demandesservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

/**
 * Entité légère en lecture seule sur GERAI.EMPLOYEES.
 * Utilisée uniquement par EmployeeRepository pour résoudre
 * les IDs Oracle depuis le JWT Keycloak.
 *
 * On n'expose pas tous les champs — uniquement ceux nécessaires
 * au service de demandes (résolution d'identité).
 */
@Entity
@Immutable
@Table(name = "EMPLOYEES")
@Getter
@NoArgsConstructor
public class EmployeeRef {

    @Id
    @Column(name = "EMPLOYEE_ID")
    private Long employeeId;

    /** UUID Keycloak — correspond au claim "sub" du JWT */
    @Column(name = "USER_ID")
    private String userId;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "DEPT_ID")
    private Long deptId;

    @Column(name = "MANAGER_ID")
    private Long managerId;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "PHOTO_URL")
    private String photoUrl;

    @Column(name = "JOB_TITLE")
    private String jobTitle;
}