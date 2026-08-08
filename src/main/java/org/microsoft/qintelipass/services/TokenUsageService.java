package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.ai.token.DepartmentUsageData;
import org.microsoft.qintelipass.ai.token.UserTokenStatus;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.entity.Models;
import org.microsoft.qintelipass.entity.TokenUsageLog;
import org.microsoft.qintelipass.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TokenUsageService {
    boolean recordTokenUsage(User user, Models model, int tokensUsed);
    boolean checkTokenLimit(User user);
    UserTokenUsageDTO getUserTokenUsage(User user);
    List<TokenUsageRankDTO> getDailyTokenRank(int topN);
    long getUserTokenLimit(User user);
    void setUserTokenLimit(User user, long limit);
    String getTodayTotalTokens();
    void increaseDailyTotalTokens(Integer tokens);
    Long getOveruseUsers();
    Long getDailyTokenLimit();
    void setDailyTokenLimit(Long value);
    Map<String, Object> getModelStatisticsForLast7Days();
    void aggregateDailyData();
    Long getActiveUserCount();
    Map<String, Object> getDepartmentStatistics();
    List<Map<String, Object>> getAllUserTokenUsage();

    long getGlobalQuota();
    long getUserQuota(Long userId);
    void setGlobalQuota(long quota);
    void setUserQuota(Long userId, long quota);

    DepartmentUsageData getDepartmentUsage();
    Map<String, Object> getUserWeeklyTrend(Long userId);
    List<Map<String, Object>> getRecentConversations(Long userId);
    Map<String, Object> getDashboardForFrontend();
    long countActiveUsers();
    List<TokenUsageLog> findByUserIdAndUsageDateBetween(Long userId, LocalDate start, LocalDate end);
}
