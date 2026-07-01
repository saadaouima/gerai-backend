package com.gerai.demandesservice.service;

import com.gerai.demandesservice.model.EmployeeRef;
import com.gerai.demandesservice.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service auxiliaire d'isolation transactionnelle pour les consultations du référentiel employé.
 * <p>
 * Encapsule chaque requête sur {@link com.gerai.demandesservice.repository.EmployeeRepository}
 * dans une transaction {@code REQUIRES_NEW} ou {@code NOT_SUPPORTED} afin qu'une erreur Oracle
 * (table manquante, résultat multiple, employé introuvable) ne propage pas l'état
 * {@code rollback-only} à la transaction appelante — ce qui provoquerait une
 * {@link org.springframework.transaction.UnexpectedRollbackException} lorsque l'appelant
 * absorbe l'exception via {@code try/catch}.
 * <p>
 * {@code @Service} : enregistre ce bean dans le contexte Spring.
 *
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class EmployeeInfoHelper {

    /** Repository read-only sur la vue GERAI.EMPLOYEES (entité {@code @Immutable}). */
    private final EmployeeRepository employeeRepo;

    /**
     * Recherche un employé par son identifiant Oracle dans une transaction isolée.
     *
     * @param empId identifiant Oracle de l'employé
     * @return un {@link Optional} contenant l'entité {@link EmployeeRef} si trouvée, vide sinon
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<EmployeeRef> findById(Long empId) {
        return employeeRepo.findById(empId);
    }

    /**
     * Retourne le nom complet ({@code FIRST_NAME || ' ' || LAST_NAME}) d'un employé
     * dans une transaction isolée.
     *
     * @param empId identifiant Oracle de l'employé
     * @return nom complet, ou {@code null} si l'employé est introuvable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String findFullName(Long empId) {
        return employeeRepo.findFullNameByEmployeeId(empId);
    }

    /**
     * Retourne l'identifiant Oracle du manager direct d'un employé (champ {@code MANAGER_ID})
     * dans une transaction isolée.
     *
     * @param empId identifiant Oracle de l'employé
     * @return identifiant Oracle du manager, ou {@code null} si non renseigné
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findManagerId(Long empId) {
        return employeeRepo.findManagerIdByEmployeeId(empId);
    }

    /**
     * Résolution hiérarchique de secours (fallback 1) : recherche le chef de projet
     * auquel l'employé appartient en tant que membre actif (table {@code PROJECT_MEMBERS}).
     * Utilisé lorsque {@code MANAGER_ID} n'est pas renseigné.
     *
     * @param empId identifiant Oracle de l'employé
     * @return identifiant Oracle du chef de projet, ou {@code null} si aucun projet actif trouvé
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findManagerIdViaProject(Long empId) {
        return employeeRepo.findManagerIdViaProjectByEmployeeId(empId);
    }

    /**
     * Résolution hiérarchique de secours (fallback 2) : recherche un employé ayant
     * {@code CHEF} ou {@code MANAGER} dans son titre de poste, dans le même département.
     * Utilisé lorsque {@code MANAGER_ID} et la résolution par projet ont échoué.
     *
     * @param empId identifiant Oracle de l'employé
     * @return identifiant Oracle du chef de département trouvé, ou {@code null} si absent
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findChefInSameDept(Long empId) {
        return employeeRepo.findChefInSameDeptByEmployeeId(empId);
    }

    /**
     * Retourne l'adresse e-mail d'un employé dans une transaction isolée.
     *
     * @param empId identifiant Oracle de l'employé
     * @return adresse e-mail de l'employé, ou {@code null} si introuvable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String findEmail(Long empId) {
        return employeeRepo.findEmailByEmployeeId(empId);
    }

    /**
     * Retourne la liste des identifiants Oracle de tous les employés ayant un rôle administrateur
     * (RH ou Admin) dans le système, dans une transaction isolée.
     * Utilisé pour la diffusion des notifications de nouvelles demandes aux gestionnaires RH.
     *
     * @return liste (non nulle) des identifiants Oracle des administrateurs actifs
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> findAdminIds() {
        List<Long> ids = employeeRepo.findAdminEmployeeIds();
        return ids != null ? ids : Collections.emptyList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> findDgIds() {
        List<Long> ids = employeeRepo.findDgEmployeeIds();
        return ids != null ? ids : Collections.emptyList();
    }

    /**
     * Recherche l'identifiant Oracle d'un employé par son UUID Keycloak ({@code sub} du JWT).
     * <p>
     * S'exécute en dehors de toute transaction ({@code NOT_SUPPORTED}) afin qu'une erreur Oracle
     * (colonne manquante, résultat multiple, etc.) ne marque pas la transaction appelante
     * comme {@code rollback-only}.
     *
     * @param sub UUID Keycloak de l'utilisateur (claim {@code sub} du JWT)
     * @return identifiant Oracle de l'employé, ou {@code null} en cas d'erreur ou d'absence
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long findEmployeeIdBySub(String sub) {
        try { return employeeRepo.findEmployeeIdByKeycloakSub(sub); } catch (Exception e) { return null; }
    }

    /**
     * Recherche l'identifiant Oracle d'un employé par son adresse e-mail.
     * <p>
     * S'exécute en dehors de toute transaction ({@code NOT_SUPPORTED}) afin qu'une erreur Oracle
     * (résultat non unique, e-mail ambigu, etc.) ne marque pas la transaction appelante
     * comme {@code rollback-only}.
     *
     * @param email adresse e-mail de l'utilisateur (claim {@code email} ou {@code preferred_username} du JWT)
     * @return identifiant Oracle de l'employé, ou {@code null} en cas d'erreur ou d'absence
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long findEmployeeIdByEmailSafe(String email) {
        try { return employeeRepo.findEmployeeIdByEmail(email); } catch (Exception e) { return null; }
    }
}
