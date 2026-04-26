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

    private boolean success;
    private T data;
    private String message;
    private String generatedAt;

    /* ── Factories ────────────────────────────────────── */

    public static <T> StatResponseDTO<T> ok(T data) {
        return StatResponseDTO.<T>builder()
                .success(true)
                .data(data)
                .generatedAt(now())
                .build();
    }

    public static <T> StatResponseDTO<T> ok(T data, String message) {
        return StatResponseDTO.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .generatedAt(now())
                .build();
    }

    public static <T> StatResponseDTO<T> error(String message) {
        return StatResponseDTO.<T>builder()
                .success(false)
                .message(message)
                .generatedAt(now())
                .build();
    }

    private static String now() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
}