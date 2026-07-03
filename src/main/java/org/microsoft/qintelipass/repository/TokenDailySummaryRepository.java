package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.TokenDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TokenDailySummaryRepository extends JpaRepository<TokenDailySummary, Long> {
    Optional<TokenDailySummary> findByUsageDateAndModelId(LocalDate usageDate, Long modelId);
    List<TokenDailySummary> findByUsageDate(LocalDate usageDate);
    List<TokenDailySummary> findByUsageDateBetween(LocalDate startDate, LocalDate endDate);
    List<TokenDailySummary> findByModelId(Long modelId);
    List<TokenDailySummary> findByModelIdAndUsageDateBetween(Long modelId, LocalDate startDate, LocalDate endDate);
    void deleteByUsageDate(LocalDate usageDate);
}