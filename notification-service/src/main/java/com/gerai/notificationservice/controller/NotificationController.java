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
 * Contrôleur REST des notifications.
 *
 * Stratégie d'identification :
 *   Le JWT Keycloak contient un claim custom "employee_id" (Long Oracle).
 *   Si absent, on retombe sur une réponse vide — pas d'erreur bloquante.
 *
 * Endpoints :
 *   GET    /api/notifications              → mes notifications
 *   GET    /api/notifications/unread       → non lues uniquement
 *   GET    /api/notifications/unread/count → compteur badge
 *   POST   /api/notifications/mark-all-read
 *   PUT    /api/notifications/{id}/read    → marquer une notification lue
 *   DELETE /api/notifications              → supprimer mes notifications
 *   POST   /api/notifications              → création manuelle (RH/ADMIN)
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:4200}")
public class NotificationController {

    private final NotificationService notificationService;
    private final JdbcTemplate        jdbcTemplate;

    /* ── Mes notifications ───────────────────────── */

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(List.of());
        String role = extractRole(auth);
        return ResponseEntity.ok(notificationService.getNotificationsByEmployeeAndRole(employeeId, role));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnread(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(notificationService.getUnreadNotifications(employeeId));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Long> countUnread(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(0L);
        return ResponseEntity.ok(notificationService.countUnread(employeeId));
    }

    /* ── Marquer comme lues ──────────────────────── */

    @PostMapping("/mark-all-read")
    public ResponseEntity<Integer> markAllAsRead(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(0);
        String role = extractRole(auth);
        int updated = notificationService.markAllAsRead(employeeId, role);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/read")
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markOneAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    /* ── Supprimer mes notifications ─────────────── */

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOne(@PathVariable Long id) {
        notificationService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMyNotifications(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId != null) {
            notificationService.deleteNotificationsByEmployee(employeeId);
        }
        return ResponseEntity.noContent().build();
    }

    /* ── Création manuelle (RH / ADMIN) ──────────── */

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