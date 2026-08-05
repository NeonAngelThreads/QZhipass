package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.PublicAgentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicAgentTemplateRepository extends JpaRepository<PublicAgentTemplate, Long> {

    List<PublicAgentTemplate> findByStatusOrderByCreatedAtAsc(String status);
    Optional<PublicAgentTemplate> findByIdAndStatus(Long id, String status);
}
