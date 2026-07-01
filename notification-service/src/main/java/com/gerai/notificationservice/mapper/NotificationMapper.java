package com.gerai.notificationservice.mapper;

import com.gerai.notificationservice.dto.CreateNotificationRequest;
import com.gerai.notificationservice.dto.NotificationDTO;
import com.gerai.notificationservice.entity.Notification;
import com.gerai.notificationservice.enums.TypeNotification;
import com.gerai.notificationservice.event.NotificationEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Mapper MapStruct pour les conversions entre l'entité {@link Notification},
 * les DTOs et les événements Kafka du microservice notification-service.
 * <p>
 * {@code @Mapper(componentModel = "spring")} : génère une implémentation Spring Bean
 * du mapper, injectable par {@code @Autowired} ou constructeur dans les services.
 * MapStruct génère le code de mapping à la compilation.
 * </p>
 *
 * @since 1.0
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /* ── Entity → DTO ────────────────────────────── */

    /**
     * Convertit une entité {@link Notification} en DTO {@link NotificationDTO}
     * pour l'exposition via l'API REST et le WebSocket STOMP.
     *
     * @param notification l'entité persistée à convertir
     * @return le DTO correspondant, prêt pour la sérialisation JSON
     */
    NotificationDTO toDTO(Notification notification);

    /* ── CreateNotificationRequest → Entity ──────── */

    /**
     * Convertit une requête de création manuelle {@link CreateNotificationRequest}
     * en entité {@link Notification} à persister.
     * <p>
     * Les champs {@code notificationId}, {@code isRead}, {@code readAt} et {@code createdAt}
     * sont ignorés (gérés par JPA ou la base de données).
     * </p>
     *
     * @param request la requête de création manuelle validée
     * @return l'entité {@link Notification} prête à être sauvegardée
     */
    @Mapping(target = "notificationId", ignore = true)
    @Mapping(target = "isRead",         ignore = true)
    @Mapping(target = "readAt",         ignore = true)
    @Mapping(target = "createdAt",      ignore = true)
    Notification toEntity(CreateNotificationRequest request);

    /* ── NotificationEvent (Kafka) → Entity ──────── */

    /**
     * Convertit un événement Kafka {@link NotificationEvent} en entité
     * {@link Notification} à persister.
     * <p>
     * Le champ {@code type} (String) est converti via la méthode utilitaire
     * {@link #mapStringToType(String)} pour tolérer les valeurs non conformes
     * à l'enum (statuts métier ou valeurs inconnues).
     * Les champs techniques ({@code notificationId}, {@code isRead}, {@code readAt},
     * {@code createdAt}) sont ignorés.
     * </p>
     *
     * @param event l'événement de notification reçu depuis Kafka
     * @return l'entité {@link Notification} prête à être sauvegardée
     */
    @Mapping(target = "notificationId", ignore = true)
    @Mapping(target = "isRead",         ignore = true)
    @Mapping(target = "readAt",         ignore = true)
    @Mapping(target = "createdAt",      ignore = true)
    @Mapping(source = "type", target = "type", qualifiedByName = "mapStringToType")
    Notification toEntity(NotificationEvent event);

    /* ── String → TypeNotification ───────────────── */

    /**
     * Convertit une chaîne de type brute en {@link TypeNotification}.
     * <p>
     * Tente d'abord une correspondance directe via {@code valueOf} (insensible à la casse),
     * puis délègue à {@link TypeNotification#fromStatut(String)} pour les statuts métier.
     * Retourne {@link TypeNotification#INFO} si aucune correspondance n'est trouvée.
     * </p>
     *
     * @param typeStr la chaîne représentant le type (ex. : {@code "NOUVELLE_DEMANDE"},
     *                {@code "VALIDEE_RH"}, {@code null})
     * @return le {@link TypeNotification} correspondant, ou {@link TypeNotification#INFO} par défaut
     */
    @Named("mapStringToType")
    default TypeNotification mapStringToType(String typeStr) {
        if (typeStr == null) return TypeNotification.INFO;
        try {
            return TypeNotification.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TypeNotification.fromStatut(typeStr);
        }
    }
}