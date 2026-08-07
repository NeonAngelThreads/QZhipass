package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.TokenUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TokenUsageLogRepository extends JpaRepository<TokenUsageLog, Long> {
    List<TokenUsageLog> findByUser_IdAndUsageDate(Long userId, LocalDate usageDate);
    List<TokenUsageLog> findByModel_IdAndUsageDate(Long modelId, LocalDate usageDate);
    List<TokenUsageLog> findByUsageDate(LocalDate usageDate);
    List<TokenUsageLog> findByUsageDateBetween(LocalDate startDate, LocalDate endDate);
    List<TokenUsageLog> findByUser_IdAndUsageDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT t.model.id, SUM(t.tokensUsed) FROM TokenUsageLog t WHERE t.usageDate = :date GROUP BY t.model.id")
    List<Object[]> sumByModelIdForDate(@Param("date") LocalDate date);

    @Query("SELECT t.user.id, SUM(t.tokensUsed) FROM TokenUsageLog t WHERE t.usageDate BETWEEN :startDate AND :endDate GROUP BY t.user.id, t.usageDate ORDER BY t.usageDate")
    List<Object[]> sumByUserIdAndDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT t.model.modelName AS model,
                   t.usageDate AS usageDate,
                   SUM(t.tokensUsed) AS total
            FROM TokenUsageLog t
            WHERE t.usageDate >= :start
            GROUP BY t.usageDate, t.model.modelName
            ORDER BY t.usageDate
            """)
    List<ModelDailyTotal> findDailyTotalsSince(@Param("start") LocalDate start);

    @Query("SELECT DISTINCT t.user.id FROM TokenUsageLog t WHERE t.usageDate = :date")
    List<Long> findDistinctUserIdsByDate(@Param("date") LocalDate date);

    @Query("SELECT t.user.id, SUM(t.tokensUsed) FROM TokenUsageLog t WHERE t.usageDate = :date GROUP BY t.user.id")
    List<Object[]> sumByUserIdForDate(@Param("date") LocalDate date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TokenUsageLog t WHERE t.usageDate < :cutoff")
    int deleteByUsageDateBefore(@Param("cutoff") LocalDate cutoff);

    interface ModelDailyTotal {
        String getModel();
        LocalDate getUsageDate();
        Long getTotal();
    }
}
