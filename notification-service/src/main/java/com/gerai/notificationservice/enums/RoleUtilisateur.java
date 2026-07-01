package com.gerai.notificationservice.enums;

/**
 * Enumération des rôles utilisateurs ciblés par les notifications broadcast Synapse.
 * <p>
 * Chaque rôle correspond à un groupe fonctionnel de l'organisation :
 * <ul>
 *   <li>{@code EMPLOYE} — reçoit les notifications relatives à ses propres demandes
 *       (approbation, rejet, rappels).</li>
 *   <li>{@code CHEF} — reçoit les notifications de nouvelles demandes à valider
 *       soumises par les membres de son équipe.</li>
 *   <li>{@code ADMIN} — reçoit les alertes système, les rapports de synthèse
 *       et les notifications de création de nouveaux comptes.</li>
 * </ul>
 * Utilisée pour router les notifications broadcast via WebSocket
 * ({@code /topic/notifications.{role}}).
 * </p>
 *
 * @since 1.0
 */
public enum RoleUtilisateur {

    /** Rôle chef d'équipe : valide les demandes et reçoit les notifications d'approbation. */
    CHEF,

    /** Rôle employé standard : reçoit les notifications sur l'état de ses demandes. */
    EMPLOYE,

    /** Rôle administrateur RH : reçoit les alertes système et les notifications globales. */
    ADMIN
}