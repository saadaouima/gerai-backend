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
@Repository
public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {

    Optional<MessageRead> findByMessage_MessageIdAndEmployeeId(
            Long messageId, Long employeeId);

    /**
     * Marque comme lus tous les messages d'une conversation
     * que l'employé n'a pas encore lus.
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

    /** Vérifie si un message a été lu par un employé */
    boolean existsByMessage_MessageIdAndEmployeeId(Long messageId, Long employeeId);
}