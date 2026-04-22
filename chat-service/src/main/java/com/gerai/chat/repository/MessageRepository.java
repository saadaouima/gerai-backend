package com.gerai.chat.repository;

import com.gerai.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository pour la table MESSAGES.
 *
 * Le statut de lecture est désormais dans MESSAGE_READS,
 * pas dans la table MESSAGES. Les méthodes de lecture
 * rejoignent MESSAGE_READS pour calculer le statut "lu".
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Tous les messages non supprimés d'une conversation,
     * triés chronologiquement.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.conversationId = :conversationId
              AND m.isDeleted = 0
            ORDER BY m.sentAt ASC
            """)
    List<Message> findByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Nombre de messages non lus dans une conversation pour un employé donné.
     * Non lu = message dont sender_id != employeeId et aucune ligne dans MESSAGE_READS.
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
     * Dernier message d'une conversation (pour l'aperçu).
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.conversationId = :conversationId
              AND m.isDeleted = 0
            ORDER BY m.sentAt DESC
            """)
    List<Message> findLastMessage(@Param("conversationId") Long conversationId);
}