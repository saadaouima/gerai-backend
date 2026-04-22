package com.gerai.chat.repository;

import com.gerai.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour la table CONVERSATIONS.
 *
 * Toutes les requêtes passent par CONVERSATION_PARTICIPANTS
 * pour identifier les conversations d'un employé.
 * Les employés sont identifiés par leur employee_id Oracle (Long),
 * pas par leur UUID Keycloak.
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Toutes les conversations actives d'un employé,
     * triées par dernier message (plus récent en premier).
     */
    @Query("""
            SELECT DISTINCT c FROM Conversation c
            JOIN c.participants p
            WHERE p.employeeId = :employeeId
              AND p.leftAt IS NULL
              AND c.isActive = 1
            ORDER BY c.lastMessageAt DESC NULLS LAST
            """)
    List<Conversation> findAllByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Trouve une conversation directe entre deux employés.
     * Utilisé pour éviter les doublons à la création.
     */
    @Query("""
            SELECT c FROM Conversation c
            JOIN c.participants p1 ON p1.employeeId = :emp1
            JOIN c.participants p2 ON p2.employeeId = :emp2
            WHERE c.type = 'DIRECT'
              AND c.isActive = 1
              AND p1.leftAt IS NULL
              AND p2.leftAt IS NULL
            """)
    Optional<Conversation> findDirectConversation(
            @Param("emp1") Long employeeId1,
            @Param("emp2") Long employeeId2);

    /**
     * Doublons éventuels (pour nettoyage à la création).
     */
    @Query("""
            SELECT c FROM Conversation c
            JOIN c.participants p1 ON p1.employeeId = :emp1
            JOIN c.participants p2 ON p2.employeeId = :emp2
            WHERE c.type = 'DIRECT'
              AND c.isActive = 1
            """)
    List<Conversation> findAllDirectConversations(
            @Param("emp1") Long employeeId1,
            @Param("emp2") Long employeeId2);
}