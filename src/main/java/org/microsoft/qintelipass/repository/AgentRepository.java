package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, String> {
}
