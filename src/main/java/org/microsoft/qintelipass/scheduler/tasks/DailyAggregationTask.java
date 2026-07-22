package org.microsoft.qintelipass.scheduler.tasks;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyAggregationTask {
    private final TokenUsageService tokenUsageService;

    @Getter
    private static Long yesterdayTokens = 0L;
    @Getter
    private static Long yesterdayOveruseUsers = 0L;


    @Autowired
    public DailyAggregationTask(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    @Scheduled(cron = "0 55 23 * * ?")
    public void aggregateDailyTokenUsage() {
        log.info("Starting daily token usage aggregation task...");
        try {
            tokenUsageService.aggregateDailyData();
            log.info("Daily token usage aggregation completed successfully");
        } catch (Exception e) {
            log.error("Failed to aggregate daily token usage", e);
        }
    }

    @Scheduled(cron = "0 59 23 * * ?")
    public void updateYesterdayInfo() {
        log.info("Starting Update Manifest...");
        try {
            yesterdayTokens = Long.valueOf(tokenUsageService.getTodayTotalTokens());
            yesterdayOveruseUsers = tokenUsageService.getOveruseUsers();
            log.info("Update was success");
        } catch (Exception e) {
            log.error("Failed to Update", e);
        }
    }
}