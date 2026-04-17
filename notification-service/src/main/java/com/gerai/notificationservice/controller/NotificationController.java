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

    /* ── Mes notifications ───────────────────────── */

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(Authentication auth) {
        Long employeeId = extractEmployeeId(auth);
        if (employeeId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(notificationService.getNotificationsByEmployee(employeeId));
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
        int updated = notificationService.markAllAsRead(employeeId);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markOneAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    /* ── Supprimer mes notifications ─────────────── */

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
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<NotificationDTO> create(
            @Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(notificationService.create(request));
    }

    /* ── Helper JWT ──────────────────────────────── */

    /**
     * Extrait l'employee_id Oracle depuis le claim custom Keycloak.
     *
     * Pour configurer ce claim dans Keycloak :
     *   Clients → notification-service → Client Scopes → Add mapper
     *   → User Attribute : attribute name = employee_id,
     *                      token claim name = employee_id,
     *                      claim type = Long
     *   Puis Users → [user] → Attributes → employee_id = [ID Oracle]
     */
    private Long extractEmployeeId(Authentication auth) {
        if (auth == null) return null;
        try {
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                Object val = jwt.getClaim("employee_id");
                if (val instanceof Number n)  return n.longValue();
                if (val instanceof String s)  return Long.parseLong(s.trim());
            }
        } catch (Exception e) {
            log.warn("[NotificationController] Impossible d'extraire employee_id du JWT : {}",
                    e.getMessage());
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