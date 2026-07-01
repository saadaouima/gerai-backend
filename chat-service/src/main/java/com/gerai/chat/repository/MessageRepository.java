package com.gerai.chat.repository;

import com.gerai.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository Spring Data JPA pour la gestion des messages.
 * <p>
 * Le statut de lecture des messages est désormais géré via la table {@code MESSAGE_READS}
 * ({@link com.gerai.chat.repository.MessageReadRepository}) et non dans la table {@code MESSAGES}.
 * Les requêtes de comptage utilisent des requêtes SQL natives avec sous-requêtes
 * pour calculer le nombre de messages non lus.
 *
 * @since 1.0
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Retourne tous les messages non supprimés d'une conversation, triés chronologiquement.
     * Les messages supprimés logiquement ({@code isDeleted = 1}) sont exclus du résultat.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @return la liste des messages actifs, du plus ancien au plus récent
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.conversationId = :conversationId
              AND m.isDeleted = 0
            ORDER BY m.sentAt ASC
            """)
    List<Message> findByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Compte le nombre de messages non lus dans une conversation pour un employé donné.
     * <p>
     * Un message est considéré non lu si :
     * <ul>
     *   <li>Son expéditeur est différent de l'employé ({@code sender_id != employeeId}).</li>
     *   <li>Il n'est pas supprimé logiquement ({@code is_deleted = 0}).</li>
     *   <li>Aucune ligne n'existe dans {@code MESSAGE_READS} pour cet employé et ce message.</li>
     * </ul>
     * Requête SQL native pour une exécution optimisée sur Oracle.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @param employeeId     l'identifiant Oracle de l'employé
     * @return le nombre de messages non lus par cet employé dans cette conversation
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM MESSAGES m
            WHERE m.CONVERSATION_ID = :conversationId
              AND m.SENDER_ID != :employeeId
              AND m.IS_DELETED = 0
              AND NOT EXISTS (
                  SELECT 1 FROM MESSAGE_READS mr
                  WHERE mr.MESSAGE_ID = m.MESSAGE_ID
                    AND mr.EMPLOYEE_ID = :employeeId
              )
            """, nativeQuery = true)
    int countUnread(@Param("conversationId") Long conversationId,
                    @Param("employeeId")     Long employeeId);

    /**
     * Retourne les messages d'une conversation triés par date d'envoi décroissante.
     * Le premier élément de la liste est donc le message le plus récent.
     * Utilisé dans {@link com.gerai.chat.service.ChatService} pour générer
     * l'aperçu du dernier message dans la liste des conversations.
     *
     * @param conversationId l'identifiant Oracle de la conversation
     * @return la liste des messages actifs, du plus récent au plus ancien
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.conversationId = :conversationId
              AND m.isDeleted = 0
            ORDER BY m.sentAt DESC
            """)
    List<Message> findLastMessage(@Param("conversationId") Long conversationId);
}