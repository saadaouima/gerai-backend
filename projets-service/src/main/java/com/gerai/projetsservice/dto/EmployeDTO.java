package com.gerai.projetsservice.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeDTO {
    private Long   id;
    private String prenom;
    private String nom;
    private String email;
    private String poste;
    private String departement;
    private String nomComplet;
}
