package com.gerai.tachesservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

/**
 * Projection légère de GERAI_USER.EMPLOYEES.
 * Seuls les champs nécessaires au service Tâches sont chargés.
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

    @Column(name = "USER_ID")
    private String userId;   // UUID Keycloak (= JWT.sub)

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
}