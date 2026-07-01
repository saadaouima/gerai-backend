package com.gerai.chat.repository;

import com.gerai.chat.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour la gestion des participants aux conversations.
 * <p>
 * {@code @Repository} : déclare cette interface comme composant de persistance Spring,
 * avec traduction automatique des exceptions JPA en exceptions Spring.
 * <p>
 * Fournit les opérations CRUD standard héritées de {@link JpaRepository}
 * et des requêtes métier spécifiques pour la gestion des accès aux conversations.
 *
 * @since 1.0
 */
@Repository
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, Long> {

    /**
     * Retourne tous les participants d'une conversation donnée.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @return la liste des participants (vide si aucun trouvé)
     */
    List<ConversationParticipant> findByConversation_ConversationId(Long conversationId);

    /**
     * Recherche un participant spécifique dans une conversation.
     * Utilisé notamment pour éviter les doublons lors de l'ajout de participants.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @param employeeId     l'identifiant Oracle de l'employé
     * @return un {@link Optional} contenant le participant, vide s'il n'existe pas
     */
    Optional<ConversationParticipant> findByConversation_ConversationIdAndEmployeeId(
            Long conversationId, Long employeeId);

    /**
     * Vérifie si un employé est participant actif (non sorti) d'une conversation.
     * Utilisé pour contrôler les accès avant l'envoi ou la lecture de messages.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @param employeeId     l'identifiant Oracle de l'employé à vérifier
     * @return {@code true} si l'employé est participant actif ({@code leftAt IS NULL})
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM ConversationParticipant p
            WHERE p.conversation.conversationId = :convId
              AND p.employeeId = :employeeId
              AND p.leftAt IS NULL
            """)
    boolean isActiveParticipant(@Param("convId")      Long conversationId,
                                @Param("employeeId")  Long employeeId);
}