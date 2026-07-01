package com.gerai.analyticsservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enveloppe générique pour toutes les réponses de stats.
 * Permet au frontend Angular de toujours avoir :
 *  - success : true/false
 *  - data    : le payload métier (DashboardSummaryDTO, CongeStatsDTO...)
 *  - message : info ou erreur lisible
 *  - generatedAt : horodatage de la réponse
 *
 * Usage dans un controller :
 *   return ResponseEntity.ok(StatResponseDTO.ok(dashboard));
 *   return ResponseEntity.ok(StatResponseDTO.error("Accès refusé"));
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatResponseDTO<T> {

    /** Indique si la requête s'est terminée avec succès. */
    private boolean success;

    /** Le payload métier sérialisé (DashboardSummaryDTO, CongeStatsDTO, etc.). */
    private T data;

    /** Message d'information ou d'erreur lisible par l'utilisateur. */
    private String message;

    /** Horodatage de génération de la réponse au format "dd/MM/yyyy HH:mm:ss". */
    private String generatedAt;

    /* ── Factories ────────────────────────────────────── */

    /**
     * Crée une réponse de succès avec un payload de données.
     *
     * @param <T>  le type du payload
     * @param data le payload à encapsuler
     * @return un {@link StatResponseDTO} avec {@code success = true}
     */
    public static <T> StatResponseDTO<T> ok(T data) {
        return StatResponseDTO.<T>builder()
                .success(true)
                .data(data)
                .generatedAt(now())
                .build();
    }

    /**
     * Crée une réponse de succès avec un payload et un message d'information.
     *
     * @param <T>     le type du payload
     * @param data    le payload à encapsuler
     * @param message le message d'information à joindre à la réponse
     * @return un {@link StatResponseDTO} avec {@code success = true} et le message
     */
    public static <T> StatResponseDTO<T> ok(T data, String message) {
        return StatResponseDTO.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .generatedAt(now())
                .build();
    }

    /**
     * Crée une réponse d'erreur sans payload.
     *
     * @param <T>     le type générique (non utilisé dans ce cas)
     * @param message le message d'erreur lisible par l'utilisateur ou le frontend
     * @return un {@link StatResponseDTO} avec {@code success = false}
     */
    public static <T> StatResponseDTO<T> error(String message) {
        return StatResponseDTO.<T>builder()
                .success(false)
                .message(message)
                .generatedAt(now())
                .build();
    }

    /**
     * Retourne l'horodatage courant formaté pour le champ {@code generatedAt}.
     *
     * @return la date et l'heure actuelles au format "dd/MM/yyyy HH:mm:ss"
     */
    private static String now() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
}