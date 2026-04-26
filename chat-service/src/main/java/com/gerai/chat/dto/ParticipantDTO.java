package com.gerai.chat.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ParticipantDTO {
    private Long   employeeId;
    private String nom;
    private String prenom;
    private String nomComplet;
    private String role;        // ADMIN | MEMBRE
    private boolean enLigne;
}