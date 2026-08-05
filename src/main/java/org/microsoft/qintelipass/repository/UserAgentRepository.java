package org.microsoft.qintelipass.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.microsoft.qintelipass.entity.UserAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserAgentRepository extends JpaRepository<UserAgent, Long> {

    Optional<UserAgent> findByIdAndUserIdAndStatus(Long id, Long userId, String status);

    Optional<UserAgent> findByIdAndUserId(Long id, Long userId);

    Optional<UserAgent> findByUserIdAndNameAndStatus(Long userId, String name, String status);

    long countByUserIdAndStatus(Long userId, String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserAgent agent where agent.id = :agentId and agent.userId = :userId")
    int hardDeleteByIdAndUserId(@Param("agentId") Long agentId, @Param("userId") Long userId);

    List<UserAgent> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String statusActive);
}
