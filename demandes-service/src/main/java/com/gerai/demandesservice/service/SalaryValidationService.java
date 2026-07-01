package com.gerai.demandesservice.service;

import com.gerai.demandesservice.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service de consultation du salaire courant d'un employé pour la validation des demandes de crédit.
 * <p>
 * La vérification salariale s'exécute en dehors de toute transaction ({@code NOT_SUPPORTED})
 * afin qu'une erreur Oracle (ex. {@code ORA-00942} si la table {@code CONTRACTS} est absente,
 * ou tout autre problème SQL) ne propage pas l'état {@code rollback-only} à la transaction appelante.
 * <p>
 * {@code @Service} : enregistre ce bean dans le contexte Spring.
 * {@code @Slf4j} : active la journalisation via Lombok.
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryValidationService {

    /** Repository pour la lecture du salaire courant depuis la table {@code GERAI.CONTRACTS}. */
    private final EmployeeRepository employeeRepo;

    /**
     * Retourne le salaire mensuel brut courant d'un employé.
     * <p>
     * S'exécute en dehors de toute transaction ({@code NOT_SUPPORTED}) pour éviter toute
     * contamination de la transaction appelante en cas d'erreur Oracle.
     * Retourne {@code null} en cas d'erreur ou si aucun contrat actif n'est trouvé.
     *
     * @param employeeId identifiant Oracle de l'employé
     * @return salaire mensuel brut en {@link BigDecimal}, ou {@code null} si non disponible
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BigDecimal getSalary(Long employeeId) {
        try {
            return employeeRepo.findCurrentSalaryByEmployeeId(employeeId);
        } catch (Exception e) {
            log.warn("[SalaryCheck] Cannot read salary for emp={}: {}", employeeId, e.getMessage());
            return null;
        }
    }
}
