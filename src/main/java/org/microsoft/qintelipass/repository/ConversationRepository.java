package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

// Repository queries always include the current MySQL user id when reading user-owned conversations.
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserIdAndStatusOrderByLastMessageAtDescUpdatedAtDescIdDesc(
            Long userId,
            String status,
            Pageable pageable
    );

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    List<Conversation> findByStatusAndFirstAnsweredAtIsNotNullAndLastMessageAtAfter(
            String status,
            LocalDateTime activeAfter
    );
}
