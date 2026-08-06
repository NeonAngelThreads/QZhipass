package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.ai.token.DashboardData;
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
    // ==================== 原有接口（我的版本） ====================
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

    // ==================== 兼容对方版本：配额管理 ====================
    long getGlobalQuota();
    long getUserQuota(Long userId);
    void setGlobalQuota(long quota);
    void setGlobalQuotaWithCount(long quota);
    void setUserQuota(Long userId, long quota);

    // ==================== 兼容对方版本：用量记录（按 userId + model 名 + prompt/completion 拆分） ====================
    void recordUsage(Long userId, String model, long promptTokens, long completionTokens);

    // ==================== 兼容对方版本：状态查询 ====================
    UserTokenStatus getDailyStatus(Long userId);
    boolean checkQuota(Long userId, long estimatedTokens);

    // ==================== 兼容对方版本：看板和统计 ====================
    DashboardData getDashboard();
    DepartmentUsageData getDepartmentUsage();
    Map<String, Object> getUserWeeklyTrend(Long userId);
    List<Map<String, Object>> getRecentConversations(Long userId);
    Map<String, Object> getDashboardForFrontend();
    long countActiveUsers();
    List<TokenUsageLog> findByUserIdAndUsageDateBetween(Long userId, LocalDate start, LocalDate end);
}
