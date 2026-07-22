package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.UserAgentRemoval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgentRemovalRepository extends JpaRepository<UserAgentRemoval, Long> {
    boolean existsByUserIdAndAgentId(Long userId, String agentId);
}
