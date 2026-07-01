package com.gerai.chat.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Objet de transfert de données représentant une conversation dans l'interface chat.
 * <p>
 * Utilisé dans les réponses REST et WebSocket pour afficher la liste des conversations
 * et leur aperçu (dernier message, nombre de non-lus, participants).
 *
 * @since 1.0
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationDTO {

    /** Identifiant unique Oracle de la conversation (PK de CONVERSATIONS). */
    private Long   conversationId;

    /** Type de conversation : {@code DIRECT}, {@code GROUPE} ou {@code ANNONCE}. */
    private String type;

    /** Nom de la conversation (généré pour DIRECT = nom de l'autre participant, sinon nom du groupe). */
    private String name;

    /** Liste des participants avec leurs noms (calculés par JOIN sur EMPLOYEES). */
    private List<ParticipantDTO> participants;

    /** Aperçu textuel du dernier message (tronqué à 50 caractères). */
    private String          dernierMessage;

    /** Horodatage du dernier message, utilisé pour le tri de la liste. */
    private LocalDateTime   lastMessageAt;

    /** Nombre de messages non lus par l'utilisateur courant dans cette conversation. */
    private int nombreNonLus;

    /** ID Oracle de l'employé connecté (permet d'identifier les messages "envoyés par moi"). */
    private Long currentEmployeeId;
}