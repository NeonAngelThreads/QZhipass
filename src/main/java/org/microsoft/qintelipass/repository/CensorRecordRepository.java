package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.CensorRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
public interface CensorRecordRepository extends JpaRepository<CensorRecord, Long> {

    long countByUserIdAndCreatedAtBetween(Long userId,
                                          LocalDateTime startTime,
                                          LocalDateTime endTime);

    Page<CensorRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<CensorRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<CensorRecord> findByUsernameContainingOrHitKeywordsContainingAllIgnoreCaseOrderByCreatedAtDesc(
            String username, String keyword, Pageable pageable);
}
