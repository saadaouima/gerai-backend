package com.gerai.chat.repository;

import com.gerai.chat.entity.MessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
/**
 * Repository Spring Data JPA pour la gestion des enregistrements de lecture de messages.
 * <p>
 * {@code @Repository} : déclare cette interface comme composant de persistance Spring,
 * avec traduction automatique des exceptions JPA en exceptions Spring.
 * <p>
 * Gère la table {@code MESSAGE_READS} qui implémente le tracking de lecture
 * message par message et employé par employé.
 *
 * @since 1.0
 */
@Repository
public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {

    /**
     * Recherche l'enregistrement de lecture d'un message spécifique par un employé.
     *
     * @param messageId  l'identifiant Oracle du message
     * @param employeeId l'identifiant Oracle de l'employé
     * @return un {@link Optional} contenant l'enregistrement de lecture s'il existe
     */
    Optional<MessageRead> findByMessage_MessageIdAndEmployeeId(
            Long messageId, Long employeeId);

    /**
     * Marque comme lus, en une seule requête SQL native, tous les messages non lus
     * d'une conversation pour un employé donné.
     * <p>
     * N'insère que les lignes pour les messages que l'employé n'a pas encore lus
     * (exclut ses propres messages et les messages déjà lus).
     * <p>
     * {@code @Modifying} : indique à Spring Data qu'il s'agit d'une requête de modification (INSERT).
     * <br>
     * {@code @Transactional} : assure l'exécution dans une transaction.
     *
     * @param conversationId l'identifiant Oracle de la conversation à marquer comme lue
     * @param employeeId     l'identifiant Oracle de l'employé lecteur
     * @param readAt         l'horodatage de lecture à enregistrer
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO MESSAGE_READS (MESSAGE_ID, EMPLOYEE_ID, READ_AT)
            SELECT m.MESSAGE_ID, :employeeId, :readAt
            FROM MESSAGES m
            WHERE m.CONVERSATION_ID = :conversationId
              AND m.SENDER_ID != :employeeId
              AND m.IS_DELETED = 0
              AND NOT EXISTS (
                  SELECT 1 FROM MESSAGE_READS mr
                  WHERE mr.MESSAGE_ID   = m.MESSAGE_ID
                    AND mr.EMPLOYEE_ID  = :employeeId
              )
            """, nativeQuery = true)
    void markAllAsRead(@Param("conversationId") Long conversationId,
                       @Param("employeeId")     Long employeeId,
                       @Param("readAt")         LocalDateTime readAt);

    /**
     * Vérifie si un message a été lu par un employé spécifique.
     * Utilisé dans {@link com.gerai.chat.service.ChatService} pour calculer
     * le champ {@code luParMoi} du {@link com.gerai.chat.dto.MessageDTO}.
     *
     * @param messageId  l'identifiant Oracle du message
     * @param employeeId l'identifiant Oracle de l'employé
     * @return {@code true} si une ligne de lecture existe pour cette combinaison
     */
    boolean existsByMessage_MessageIdAndEmployeeId(Long messageId, Long employeeId);
}