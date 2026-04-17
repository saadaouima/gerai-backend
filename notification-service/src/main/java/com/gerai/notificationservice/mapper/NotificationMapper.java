package com.gerai.notificationservice.mapper;

import com.gerai.notificationservice.dto.CreateNotificationRequest;
import com.gerai.notificationservice.dto.NotificationDTO;
import com.gerai.notificationservice.entity.Notification;
import com.gerai.notificationservice.enums.TypeNotification;
import com.gerai.notificationservice.event.NotificationEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /* ── Entity → DTO ────────────────────────────── */

    NotificationDTO toDTO(Notification notification);

    /* ── CreateNotificationRequest → Entity ──────── */

    @Mapping(target = "notificationId", ignore = true)
    @Mapping(target = "isRead",         ignore = true)
    @Mapping(target = "readAt",         ignore = true)
    @Mapping(target = "createdAt",      ignore = true)
    Notification toEntity(CreateNotificationRequest request);

    /* ── NotificationEvent (Kafka) → Entity ──────── */

    @Mapping(target = "notificationId", ignore = true)
    @Mapping(target = "isRead",         ignore = true)
    @Mapping(target = "readAt",         ignore = true)
    @Mapping(target = "createdAt",      ignore = true)
    @Mapping(source = "type", target = "type", qualifiedByName = "mapStringToType")
    Notification toEntity(NotificationEvent event);

    /* ── String → TypeNotification ───────────────── */

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