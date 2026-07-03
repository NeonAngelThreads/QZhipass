package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.TokenUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TokenUsageLogRepository extends JpaRepository<TokenUsageLog, Long> {
    List<TokenUsageLog> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
    List<TokenUsageLog> findByModelIdAndUsageDate(Long modelId, LocalDate usageDate);
    List<TokenUsageLog> findByUsageDate(LocalDate usageDate);
    List<TokenUsageLog> findByUsageDateBetween(LocalDate startDate, LocalDate endDate);
    @Query("SELECT t.modelId, SUM(t.tokensUsed) FROM TokenUsageLog t WHERE t.usageDate = :date GROUP BY t.modelId")
    List<Object[]> sumByModelIdForDate(@Param("date") LocalDate date);
    @Query("SELECT t.modelId, t.usageDate, SUM(t.tokensUsed) FROM TokenUsageLog t WHERE t.usageDate BETWEEN :startDate AND :endDate GROUP BY t.modelId, t.usageDate ORDER BY t.usageDate")
    List<Object[]> sumByModelIdAndDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    @Query("SELECT DISTINCT t.userId FROM TokenUsageLog t WHERE t.usageDate = :date")
    List<Long> findDistinctUserIdsByDate(@Param("date") LocalDate date);
    @Query("SELECT t.userId, SUM(t.tokensUsed) FROM TokenUsageLog t WHERE t.usageDate = :date GROUP BY t.userId")
    List<Object[]> sumByUserIdForDate(@Param("date") LocalDate date);
}