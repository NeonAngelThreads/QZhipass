package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Repository queries always include the current MySQL user id when reading user-owned conversations.
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUser_IdAndStatusOrderByLastMessageAtDescUpdatedAtDescIdDesc(
            Long userId,
            String status,
            Pageable pageable
    );

    Optional<Conversation> findByIdAndUser_Id(Long id, Long userId);
    List<Conversation> findByUserIdAndStatusAndUserDeletedFalseOrderByLastMessageAtDescUpdatedAtDescIdDesc(
            Long userId,
            String status,
            Pageable pageable
    );

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    List<Conversation> findByStatusOrderByLastMessageAtDescUpdatedAtDescIdDesc(
            String status,
            Pageable pageable
    );

    List<Conversation> findByStatusAndFirstAnsweredAtIsNotNullAndLastMessageAtAfter(
            String status,
            LocalDateTime activeAfter
    );

    @Modifying
    @Query("DELETE FROM Conversation c")
    void deleteAllInBulk();
}
