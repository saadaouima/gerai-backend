package com.gerai.demandesservice.service;

import com.gerai.demandesservice.exception.QuotaExceededException;
import com.gerai.demandesservice.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Service de vérification et d'application des quotas de congés selon la législation tunisienne.
 * <p>
 * Gère trois types de contraintes :
 * <ul>
 *   <li>Quota annuel par type de congé ({@link #checkAnnualQuota}) — ex. 30 jours pour le congé annuel</li>
 *   <li>Utilisation unique sur toute la carrière ({@link #checkOnceInCareer}) — ex. congé Hajj</li>
 *   <li>Classification salariale et médiale ({@link #isHalfSalary}, {@link #requiresMedicalCommittee})</li>
 * </ul>
 * <p>
 * Chaque méthode transactionnelle s'exécute dans une transaction {@code REQUIRES_NEW} afin
 * qu'une erreur de vérification des quotas ne propage pas l'état {@code rollback-only}
 * à la transaction appelante.
 * <p>
 * {@code @Service} : enregistre ce bean dans le contexte Spring.
 * {@code @Slf4j} : active la journalisation via Lombok.
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveQuotaService {

    /** Repository pour le cumul des jours de congé déjà pris ou en attente. */
    private final LeaveRequestRepository leaveRepo;
    /** JdbcTemplate pour les requêtes sur la table {@code CAREER_LEAVE_USED}. */
    private final JdbcTemplate           jdbc;

    /** Association leaveTypeId → plafond annuel en jours ouvrés ({@code null} = illimité). */
    private static final Map<Long, Integer> ANNUAL_CAPS = Map.of(
        1L,  30,   // Congé annuel
        4L,  90,   // Congé sans solde (3 months ≈ 90 days)
        5L,  60,   // Congé maternité (2 months)
        8L,  6,    // Congé familial
        10L, 120,  // Congé postnatal (4 months)
        11L, 180,  // Repos allaitement (6 months)
        13L, 3,    // Congé naissance père
        6L,  3     // Congé paternité (legacy)
    );

    /** Identifiants des types de congé ne pouvant être accordés qu'une seule fois dans la carrière (ex. Hajj). */
    private static final java.util.Set<Long> ONCE_IN_CAREER = java.util.Set.of(9L); // HAJJ

    /** Identifiants des types de congé payés à demi-salaire (congé postnatal, repos d'allaitement). */
    static final java.util.Set<Long> HALF_SALARY_TYPES = java.util.Set.of(10L, 11L); // POSTNATAL, ALLAITEMENT

    /** Identifiants des types de congé nécessitant un avis de la commission médicale avant traitement normal (longue maladie). */
    static final java.util.Set<Long> MEDICAL_COMMITTEE_TYPES = java.util.Set.of(12L); // LONGUE_MALADIE

    /**
     * Vérifie que l'employé ne dépasse pas le quota annuel pour le type de congé donné.
     * Cumule les jours déjà approuvés et en attente pour l'année en cours.
     * <p>
     * S'exécute dans une transaction {@code REQUIRES_NEW} pour isoler l'erreur de quota.
     *
     * @param employeeId    identifiant Oracle de l'employé
     * @param leaveTypeId   identifiant du type de congé (ex. {@code 1} = Annuel, {@code 5} = Maternité)
     * @param requestedDays nombre de jours ouvrés demandés
     * @param year          année civile de référence
     * @throws QuotaExceededException si le total (utilisé + demandé) dépasse le plafond
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAnnualQuota(Long employeeId, Long leaveTypeId,
                                 BigDecimal requestedDays, int year) {
        Integer cap = ANNUAL_CAPS.get(leaveTypeId);
        if (cap == null) return; // unlimited type

        BigDecimal used = leaveRepo.sumPendingAndApprovedDaysByTypeAndYear(employeeId, leaveTypeId, year);
        if (used == null) used = BigDecimal.ZERO;

        BigDecimal total = used.add(requestedDays != null ? requestedDays : BigDecimal.ZERO);
        if (total.compareTo(BigDecimal.valueOf(cap)) > 0) {
            int remaining = Math.max(0, cap - used.intValue());
            throw new QuotaExceededException(
                String.format("Quota dépassé pour ce type de congé. Solde restant : %d jour(s).", remaining),
                leaveTypeIdToCode(leaveTypeId), remaining);
        }
    }

    /**
     * Vérifie que l'employé n'a pas déjà utilisé un type de congé à utilisation unique
     * (ex. congé Hajj, leaveTypeId = 9) au cours de sa carrière.
     * La traçabilité est assurée par la table {@code GERAI.CAREER_LEAVE_USED}.
     * <p>
     * S'exécute dans une transaction {@code REQUIRES_NEW} pour isoler l'erreur de quota.
     *
     * @param employeeId  identifiant Oracle de l'employé
     * @param leaveTypeId identifiant du type de congé à vérifier
     * @throws QuotaExceededException si le congé a déjà été utilisé au cours de la carrière
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkOnceInCareer(Long employeeId, Long leaveTypeId) {
        if (!ONCE_IN_CAREER.contains(leaveTypeId)) return;
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM GERAI.CAREER_LEAVE_USED WHERE EMPLOYEE_ID = ? AND LEAVE_TYPE_ID = ?",
            Integer.class, employeeId, leaveTypeId);
        if (count != null && count > 0) {
            throw new QuotaExceededException(
                "Ce type de congé ne peut être accordé qu'une seule fois dans la carrière.",
                leaveTypeIdToCode(leaveTypeId), 0);
        }
    }

    /**
     * Enregistre l'utilisation d'un type de congé à usage unique dans la table
     * {@code GERAI.CAREER_LEAVE_USED} après approbation de la demande.
     * Les erreurs d'insertion sont loguées et ignorées (non bloquantes).
     * <p>
     * S'exécute dans une transaction {@code REQUIRES_NEW} pour que l'échec d'enregistrement
     * ne bloque pas la validation de la demande.
     *
     * @param employeeId  identifiant Oracle de l'employé
     * @param leaveTypeId identifiant du type de congé utilisé
     * @param requestId   identifiant de la demande de congé approuvée (traçabilité)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCareerUsage(Long employeeId, Long leaveTypeId, Long requestId) {
        try {
            jdbc.update(
                "INSERT INTO GERAI.CAREER_LEAVE_USED (EMPLOYEE_ID, LEAVE_TYPE_ID, REQUEST_ID) VALUES (?,?,?)",
                employeeId, leaveTypeId, requestId);
        } catch (Exception e) {
            log.warn("[QuotaService] Could not record career usage: {}", e.getMessage());
        }
    }

    /**
     * Indique si un type de congé est rémunéré à demi-salaire selon la législation tunisienne.
     * Concerne le congé postnatal (leaveTypeId = 10) et le repos d'allaitement (leaveTypeId = 11).
     *
     * @param leaveTypeId identifiant du type de congé
     * @return {@code true} si le congé est à demi-salaire, {@code false} sinon
     */
    public boolean isHalfSalary(Long leaveTypeId) {
        return HALF_SALARY_TYPES.contains(leaveTypeId);
    }

    /**
     * Indique si un type de congé nécessite un avis préalable de la commission médicale
     * avant de suivre le workflow d'approbation hiérarchique normal.
     * Concerne le congé longue maladie (leaveTypeId = 12).
     *
     * @param leaveTypeId identifiant du type de congé
     * @return {@code true} si la commission médicale doit valider en premier, {@code false} sinon
     */
    public boolean requiresMedicalCommittee(Long leaveTypeId) {
        return MEDICAL_COMMITTEE_TYPES.contains(leaveTypeId);
    }

    /**
     * Convertit un identifiant de type de congé Oracle en code métier lisible.
     * Utilisé pour les messages d'erreur, les notifications et les réponses API.
     *
     * @param id identifiant Oracle du type de congé ({@code LEAVE_TYPE_ID})
     * @return code métier en majuscules (ex. {@code ANNUEL}, {@code MALADIE}, {@code MATERNITE}),
     *         ou {@code CONGE} si l'identifiant est nul ou inconnu
     */
    public String leaveTypeIdToCode(Long id) {
        if (id == null) return "CONGE";
        return switch (id.intValue()) {
            case 1  -> "ANNUEL";
            case 2  -> "MALADIE";
            case 3  -> "RTT";
            case 4  -> "SANS_SOLDE";
            case 5  -> "MATERNITE";
            case 6  -> "NAISSANCE_PERE";
            case 7  -> "CONGE";
            case 8  -> "FAMILIAL";
            case 9  -> "HAJJ";
            case 10 -> "POSTNATAL";
            case 11 -> "ALLAITEMENT";
            case 12 -> "LONGUE_MALADIE";
            case 13 -> "NAISSANCE_PERE";
            case 14 -> "CREATION_ENTREPRISE";
            case 15 -> "OBLIGATIONS_LEGALES";
            default -> "CONGE";
        };
    }
}
