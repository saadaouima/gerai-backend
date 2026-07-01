package com.gerai.chat.repository;

import com.gerai.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour la gestion des conversations.
 * <p>
 * Toutes les requêtes de recherche passent par la table {@code CONVERSATION_PARTICIPANTS}
 * pour identifier les conversations d'un employé. Les employés sont identifiés
 * par leur {@code employee_id} Oracle (Long), et non par leur UUID Keycloak.
 * <p>
 * Hérite des opérations CRUD standard de {@link JpaRepository}
 * et ajoute des requêtes JPQL métier optimisées pour les cas d'usage du chat.
 *
 * @since 1.0
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Retourne toutes les conversations actives d'un employé,
     * triées par date du dernier message (la plus récente en premier).
     * N'inclut que les conversations où l'employé est encore participant actif
     * ({@code leftAt IS NULL}) et qui n'ont pas été archivées ({@code isActive = 1}).
     *
     * @param employeeId l'identifiant Oracle de l'employé
     * @return la liste des conversations actives, triées par activité décroissante
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
     * Trouve une conversation directe ({@code DIRECT}) existante entre deux employés.
     * Utilisé lors de la création d'une conversation pour éviter les doublons.
     *
     * @param employeeId1 identifiant Oracle du premier employé
     * @param employeeId2 identifiant Oracle du second employé
     * @return un {@link Optional} contenant la conversation si elle existe
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
     * Retourne toutes les conversations directes entre deux employés
     * (inclut les conversations partiellement créées sans {@code leftAt}).
     * Utilisé lors de la récupération/création pour détecter et corriger les états incohérents.
     *
     * @param employeeId1 identifiant Oracle du premier employé
     * @param employeeId2 identifiant Oracle du second employé
     * @return la liste de toutes les conversations directes entre ces deux employés
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
    List<Conversation> findAllDirectConversations(
            @Param("emp1") Long employeeId1,
            @Param("emp2") Long employeeId2);
}