package com.gerai.projetsservice.config;

import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Contrat d'extraction des informations métier depuis le JWT Keycloak.
 * <p>
 * Permet l'injection de l'implémentation concrète ({@link JwtHelper}) dans les
 * services et contrôleurs, facilitant le test unitaire par substitution de mock.
 * </p>
 *
 * @since 1.0
 */
public interface JwtHelperInterface {

    /**
     * Résout l'identifiant Oracle de l'employé authentifié.
     *
     * @param auth le contexte d'authentification courant
     * @return l'identifiant employé ({@code EMPLOYEES.EMPLOYEE_ID})
     * @throws RuntimeException si l'employé est introuvable en base
     */
    Long getEmployeeId(Authentication auth);

    /**
     * Retourne l'adresse email de l'utilisateur depuis le claim JWT.
     *
     * @param auth le contexte d'authentification courant
     * @return l'email, ou {@code null} si absent
     */
    String getEmail(Authentication auth);

    /**
     * Retourne la liste des rôles Keycloak de l'utilisateur.
     *
     * @param auth le contexte d'authentification courant
     * @return liste des rôles (ex. {@code ["CHEF", "EMPLOYE"]})
     */
    List<String> getRoles(Authentication auth);

    /**
     * Vérifie la présence du rôle {@code CHEF}.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si l'utilisateur est chef de projet
     */
    boolean isChef(Authentication auth);

    /**
     * Vérifie la présence du rôle {@code RH}.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si l'utilisateur est responsable RH
     */
    boolean isRh(Authentication auth);

    /**
     * Vérifie la présence du rôle {@code ADMIN}.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si l'utilisateur est administrateur
     */
    boolean isAdmin(Authentication auth);

    /**
     * Vérifie si l'utilisateur est administrateur ou responsable RH.
     *
     * @param auth le contexte d'authentification courant
     * @return {@code true} si le rôle RH ou ADMIN est présent
     */
    boolean isAdminOrRh(Authentication auth);
}
