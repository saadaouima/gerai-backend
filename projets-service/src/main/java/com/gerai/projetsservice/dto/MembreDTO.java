package com.gerai.projetsservice.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MembreDTO {
    private Long   id;
    private String prenom;
    private String nom;
    private String nomComplet;
    private String initiales;
    private String role;
    private String poste;
    private String email;
    private String telephone;
    private String statut;
    private String dateEmbauche;
    private String departement;
    private String photo;
}
