package com.gerai.demandesservice.controller;

import com.gerai.demandesservice.exception.QuotaExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global des exceptions REST du demandes-service.
 * <p>
 * {@code @RestControllerAdvice} : intercepte les exceptions levées par tous les contrôleurs
 * et retourne une réponse JSON structurée avec le code HTTP approprié.
 * <p>
 * Toutes les réponses d'erreur suivent le format :
 * <pre>
 * { "status": 4xx/5xx, "error": "message HTTP", "message": "détail", "timestamp": "ISO-8601" }
 * </pre>
 *
 * @since 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Gère les dépassements de quota de congés ({@link QuotaExceededException}).
     * Retourne un code 422 Unprocessable Entity avec le type de congé et le solde restant.
     *
     * @param ex l'exception de quota dépassé
     * @return réponse HTTP 422 avec le détail du quota
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleQuotaExceeded(QuotaExceededException ex) {
        log.warn("[DemandesService] QuotaExceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status",        422,
                "error",         "QUOTA_EXCEEDED",
                "message",       ex.getMessage(),
                "leaveType",     ex.getLeaveType(),
                "remainingDays", ex.getRemainingDays(),
                "timestamp",     java.time.Instant.now().toString()
        ));
    }

    /**
     * Gère les états métier invalides (ex : voter deux fois sur un crédit).
     *
     * @param ex l'exception d'état illégal
     * @return réponse HTTP 400 Bad Request avec le message d'erreur
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.error("[DemandesService] IllegalStateException: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Gère les arguments invalides (ex : identifiant de demande introuvable).
     *
     * @param ex l'exception d'argument illégal
     * @return réponse HTTP 400 Bad Request avec le message d'erreur
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("[DemandesService] IllegalArgumentException: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Gère les violations de contraintes Oracle (clé unique, NOT NULL, CHECK).
     *
     * @param ex l'exception de violation de contrainte de données
     * @return réponse HTTP 400 Bad Request avec le message de contrainte Oracle
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDbConstraint(DataIntegrityViolationException ex) {
        String root = ex.getMostSpecificCause().getMessage();
        log.error("[DemandesService] DB constraint violation: {}", root);
        return error(HttpStatus.BAD_REQUEST, "Erreur de contrainte base de données : " + root);
    }

    /**
     * Gère les erreurs de parsing JSON dans le corps de la requête.
     *
     * @param ex l'exception de corps de requête illisible
     * @return réponse HTTP 400 Bad Request avec le détail de l'erreur JSON
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadJson(HttpMessageNotReadableException ex) {
        log.error("[DemandesService] JSON parse error: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Corps de la requête invalide : " + ex.getMostSpecificCause().getMessage());
    }

    /**
     * Gère les échecs de validation Bean Validation ({@code @NotNull}, {@code @Valid}, etc.).
     * Consolide tous les messages de validation des champs en une seule chaîne.
     *
     * @param ex l'exception de validation des arguments de méthode
     * @return réponse HTTP 400 Bad Request avec la liste des erreurs de validation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " : " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.error("[DemandesService] Validation error: {}", details);
        return error(HttpStatus.BAD_REQUEST, "Validation échouée — " + details);
    }

    /**
     * Gère les refus d'accès Spring Security (@PreAuthorize).
     * Sans ce handler, AccessDeniedException remonterait jusqu'au filet générique et retournerait 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[DemandesService] Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, "Accès refusé : " + ex.getMessage());
    }

    /**
     * Filet de sécurité pour toutes les exceptions non gérées spécifiquement.
     * Loggue la stack trace complète pour faciliter le diagnostic.
     *
     * @param ex l'exception inattendue
     * @return réponse HTTP 500 Internal Server Error avec la classe et le message de l'exception
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("[DemandesService] Unexpected error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur interne : " + ex.getClass().getSimpleName() + " — " + ex.getMessage());
    }

    /**
     * Construit la réponse d'erreur JSON standardisée.
     *
     * @param status  code HTTP de l'erreur
     * @param message message descriptif de l'erreur (ou "Erreur inconnue" si null)
     * @return une {@link ResponseEntity} contenant le corps d'erreur JSON
     */
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status",    status.value(),
                "error",     status.getReasonPhrase(),
                "message",   message != null ? message : "Erreur inconnue",
                "timestamp", Instant.now().toString()
        ));
    }
}
