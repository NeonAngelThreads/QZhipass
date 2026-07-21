package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.ConversationMemory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, Long> {
}
