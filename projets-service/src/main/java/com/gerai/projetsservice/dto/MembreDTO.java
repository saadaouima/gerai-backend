package com.gerai.projetsservice.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MembreDTO {
    private Long   id;       // employee_id Oracle
    private String prenom;
    private String nom;
    private String nomComplet;
    private String initiales;
    private String role;     // CHEF | LEAD | MEMBRE | OBSERVATEUR
    private String poste;
}
