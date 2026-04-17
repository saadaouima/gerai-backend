package com.gerai.chat.dto;

import lombok.*;

@Data @AllArgsConstructor @NoArgsConstructor
public class UserDTO {
    /** UUID Keycloak */
    private String keycloakId;
    /** ID Oracle EMPLOYEES.employee_id (depuis le claim JWT employee_id) */
    private Long   employeeId;
    private String nom;
    private String prenom;
    private String nomComplet;
    private String username;
    private boolean enLigne;
}