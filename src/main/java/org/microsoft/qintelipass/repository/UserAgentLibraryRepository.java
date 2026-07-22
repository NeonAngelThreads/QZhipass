package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.UserAgentLibrary;
import org.microsoft.qintelipass.models.UserAgentLibraryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAgentLibraryRepository extends JpaRepository<UserAgentLibrary, UserAgentLibraryId> {
    List<UserAgentLibrary> findByUserId(Long userId);
    boolean existsByUserIdAndAgentId(Long userId, Long agentId);
    long countByUserId(Long userId);
    void deleteByUserIdAndAgentId(Long userId, Long agentId);
}
