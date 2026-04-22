package com.gerai.chat.repository;

import com.gerai.chat.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/* ═══════════════════════════════════════════════════════
   ConversationParticipantRepository
   ═══════════════════════════════════════════════════════ */

@Repository
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, Long> {

    List<ConversationParticipant> findByConversation_ConversationId(Long conversationId);

    Optional<ConversationParticipant> findByConversation_ConversationIdAndEmployeeId(
            Long conversationId, Long employeeId);

    /** Vérifie si un employé est participant actif d'une conversation */
    @Query("""
            SELECT COUNT(p) > 0 FROM ConversationParticipant p
            WHERE p.conversation.conversationId = :convId
              AND p.employeeId = :employeeId
              AND p.leftAt IS NULL
            """)
    boolean isActiveParticipant(@Param("convId")      Long conversationId,
                                @Param("employeeId")  Long employeeId);
}