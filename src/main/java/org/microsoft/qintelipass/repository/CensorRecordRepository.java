package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.CensorRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CensorRecordRepository extends JpaRepository<CensorRecord, Long> {

    long countByUserIdAndCreatedAtBetween(Long userId,
                                          LocalDateTime startTime,
                                          LocalDateTime endTime);
    List<CensorRecord> findAllByOrderByCreatedAtDesc();
}