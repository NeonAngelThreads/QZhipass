package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    List<Agent> findByDeletedFalseAndCreatedByOrderByCreatedAtDesc(Long userId);
    List<Agent> findByDeletedFalseAndCreatedByIsNullOrderByIdAsc();
    long countByDeletedFalseAndCreatedBy(Long userId);
    boolean existsByDeletedFalseAndCreatedByAndNameIgnoreCase(Long userId, String name);
    boolean existsByDeletedFalseAndCreatedByIsNullAndNameIgnoreCase(String name);
}
