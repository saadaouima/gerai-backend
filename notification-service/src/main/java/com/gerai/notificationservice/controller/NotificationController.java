package com.gerai.notificationservice.controller;

import com.gerai.notificationservice.dto.CreateNotificationRequest;
import com.gerai.notificationservice.dto.NotificationDTO;
import com.gerai.notificationservice.event.NotificationEvent;
import com.gerai.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST exposant l'API de gestion des notifications aux clients Angular.
 * <p>
 * {@code @RestController} : indique que cette classe est un contrôleur REST dont
 * les méthodes retournent directement des objets sérialisés en JSON.<br>
 * {@code @RequestMapping("/api/notifications")} : préfixe d'URL de tous les endpoints
 * de ce contrôleur (authentification JWT requise, cf. {@code SecurityConfig}).<br>
 * {@code @CrossOrigin} : autorise les requêtes CORS depuis le frontend Angular
 * ({@code localhost:4200} par défaut).
 * </p>
 * <p>
 * Stratégie d'identification de l'employé connecté : le JWT Keycloak contient un
 * claim custom {@code employee_id} (identifiant Oracle {@code Long}). En l'absence
 * de ce claim, un repli sur requête SQL est effectué. Si la résolution échoue,
 * une réponse vide est renvoyée sans erreur bloquante.
 * </p>
 * <p>
 * Endpoints exposés :
 * <ul>
 *   <li>{@code GET    /api/notifications}              — toutes les notifications de l'employé connecté</li>
 *   <li>{@code GET    /api/notifications/unread}       — notifications non lues uniquement</li>
 *   <li>{@code GET    /api/notifications/unread/count} — compteur de badge non lu</li>
 *   <li>{@code POST   /api/notifications/mark-all-read} — marquer toutes les notifications comme lues</li>
 *   <li>{@code PUT    /api/notifications/{id}/read}   — marquer une notification individuelle comme lue</li>
 *   <li>{@code DELETE /api/notifications/{id}}        — supprimer une notification par identifiant</li>
 *   <li>{@code DELETE /api/notifications}             — supprimer toutes les notifications de l'employé</li>
 *   <li>{@code POST   /api/notifications}             — création manuelle (rôles RH/ADMIN uniquement)</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class NotificationController {

    /** Service métier de gestion des notifications. */
    private final NotificationService notificationService;

    /** Template JDBC pour la résolution de l'EMPLOYEE_ID via la table EMPLOYEES en fallback. */
    private final JdbcTemplate        jdbcTemplate;

    /* ── Mes notifications ───────────────────────── */

    /**
     * Retourne toutes les notifications de l'employé connecté,
     * incluant les broadcasts destinés à son rôle.
     *
     * @param auth l'objet d'authentification Spring Security contenant le JWT Keycloak
     * @return la liste des notifications triées par date décroissante,
     *         ou une liste vide si l'employé n'est pas résolvable
     */
    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(List.of());
        String role = extractRole(auth);
        return ResponseEntity.ok(notificationService.getNotificationsByEmployeeAndRole(employeeId, role));
    }

    /**
     * Retourne uniquement les notifications non lues de l'employé connecté.
     *
     * @param auth l'objet d'authentification Spring Security
     * @return la liste des notifications non lues triées par date décroissante,
     *         ou une liste vide si l'employé n'est pas résolvable
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnread(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(notificationService.getUnreadNotifications(employeeId));
    }

    /**
     * Retourne le nombre de notifications non lues de l'employé connecté.
     * Utilisé par le frontend Angular pour alimenter le badge de notification.
     *
     * @param auth l'objet d'authentification Spring Security
     * @return le nombre de notifications non lues, ou {@code 0} si l'employé est non résolvable
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Long> countUnread(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(0L);
        return ResponseEntity.ok(notificationService.countUnread(employeeId));
    }

    /* ── Marquer comme lues ──────────────────────── */

    /**
     * Marque toutes les notifications non lues de l'employé connecté (personnelles
     * et broadcasts de son rôle) comme lues.
     *
     * @param auth l'objet d'authentification Spring Security
     * @return le nombre de notifications effectivement mises à jour
     */
    @PostMapping("/mark-all-read")
    public ResponseEntity<Integer> markAllAsRead(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(0);
        String role = extractRole(auth);
        int updated = notificationService.markAllAsRead(employeeId, role);
        return ResponseEntity.ok(updated);
    }

    /**
     * Marque une notification individuelle comme lue (accessible via PUT ou PATCH).
     *
     * @param id l'identifiant Oracle de la notification à marquer comme lue
     * @return le DTO de la notification mise à jour avec son horodatage de lecture
     */
    @PutMapping("/{id}/read")
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markOneAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    /* ── Supprimer mes notifications ─────────────── */

    /**
     * Supprime une notification par son identifiant.
     *
     * @param id l'identifiant Oracle de la notification à supprimer
     * @return HTTP 204 No Content si la suppression réussit
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOne(@PathVariable Long id) {
        notificationService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Supprime toutes les notifications personnelles de l'employé connecté.
     *
     * @param auth l'objet d'authentification Spring Security
     * @return HTTP 204 No Content
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteMyNotifications(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId != null) {
            notificationService.deleteNotificationsByEmployee(employeeId);
        }
        return ResponseEntity.noContent().build();
    }

    /* ── Création manuelle (RH / ADMIN) ──────────── */

    /**
     * Crée manuellement une notification via l'interface d'administration.
     * Réservé aux utilisateurs ayant le rôle RH, ADMIN ou ADMIN_RH.
     *
     * @param request le corps de la requête contenant les données de la notification
     *                (validé par Bean Validation)
     * @return HTTP 201 Created avec le DTO de la notification créée
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN','ADMIN_RH')")
    public ResponseEntity<NotificationDTO> create(
            @Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(notificationService.create(request));
    }

    /* ── Helpers JWT ─────────────────────────────── */

    /**
     * Resolves the user's role (ADMIN / CHEF / EMPLOYE) from JWT realm_access.roles.
     */
    private String extractRole(Authentication auth) {
        if (auth == null) return "EMPLOYE";
        try {
            if (auth.getPrincipal() instanceof Jwt jwt) {
                Object realmAccess = jwt.getClaim("realm_access");
                if (realmAccess instanceof java.util.Map<?, ?> map) {
                    Object roles = map.get("roles");
                    if (roles instanceof java.util.List<?> roleList) {
                        for (Object r : roleList) {
                            String rs = String.valueOf(r);
                            if ("admin".equals(rs) || "admin_rh".equals(rs)) return "ADMIN";
                        }
                        for (Object r : roleList) {
                            if ("chef".equals(String.valueOf(r))) return "CHEF";
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[NotificationController] Impossible d'extraire le rôle du JWT: {}", e.getMessage());
        }
        return "EMPLOYE";
    }


    /**
     * Resolves the Oracle EMPLOYEE_ID for the authenticated user.
     * 1. Reads the employee_id custom JWT claim (fast path).
     * 2. Falls back to a DB lookup on EMPLOYEES.USER_ID = Keycloak sub
     *    (works without a Keycloak mapper, same strategy as WebSocketConfig).
     */
    private Long extractEmployeeId(Authentication auth) {
        if (auth == null) return null;
        try {
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                // 1. JWT claim (preferred — set up a Keycloak mapper to avoid the DB hit)
                Object val = jwt.getClaim("employee_id");
                if (val instanceof Number n)  return n.longValue();
                if (val instanceof String s && !s.isBlank()) return Long.parseLong(s.trim());

                // 2. DB fallback using Keycloak sub → EMPLOYEES.USER_ID
                String sub = jwt.getSubject();
                if (sub != null && !sub.isBlank()) {
                    try {
                        Long oracleId = jdbcTemplate.queryForObject(
                                "SELECT EMPLOYEE_ID FROM EMPLOYEES WHERE USER_ID = ?",
                                Long.class, sub);
                        if (oracleId != null) {
                            log.debug("[NotificationController] DB resolved employee_id={} for sub={}", oracleId, sub);
                            return oracleId;
                        }
                    } catch (Exception dbEx) {
                        log.warn("[NotificationController] DB lookup failed for sub={}: {}", sub, dbEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[NotificationController] Impossible d'extraire employee_id du JWT : {}", e.getMessage());
        }
        return null;
    }

    /**
     * Endpoint de test pour vérifier l'envoi d'email depuis le service de notification.
     * Simule un événement Kafka entrant et déclenche l'envoi d'un email vers l'adresse fournie.
     * <p>
     * À usage strictement interne / développement. Ne pas exposer en production.
     * </p>
     *
     * @param emailDestinataire l'adresse email qui recevra le message de test
     * @return un message de confirmation indiquant que l'envoi a été déclenché
     */
    @PostMapping("/test-email-direct")
    public ResponseEntity<String> testEmailDirect(@RequestParam String emailDestinataire) {
        log.info("Déclenchement d'un test email pour : {}", emailDestinataire);

        // On simule un événement qui arrive de Kafka
        NotificationEvent event = NotificationEvent.builder()
                .employeeId(3L) // Nour (doit exister dans ta table EMPLOYEES)
                .email(emailDestinataire)
                .type("INFO")
                .title("Test de Notification GerAI")
                .content("Félicitations ! Le service d'emailing fonctionne avec Gmail.")
                .sourceService("TEST_MANUEL")
                .build();

        // On appelle la méthode qui contient la logique d'envoi d'email
        notificationService.processNotificationEvent(event);

        return ResponseEntity.ok("Le processus d'envoi a été lancé. Vérifiez votre boîte mail !");
    }
}