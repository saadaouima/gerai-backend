package com.gerai.notificationservice.enums;

/**
 * Définit les rôles cibles pour l'affichage des notifications dans GerAI.
 * * EMPLOYE : Reçoit les notifications sur ses demandes (Approuvée/Rejetée).
 * CHEF    : Reçoit les notifications de nouvelles demandes à valider.
 * ADMIN   : Reçoit les alertes système, rapports de synthèse ou nouveaux comptes.
 */
public enum RoleUtilisateur {
    CHEF,
    EMPLOYE,
    ADMIN
}