package org.microsoft.qintelipass.token;

import java.util.List;
import java.util.Map;

/**
 * 管理员可视化看板数据
 */
public record DashboardData(
        long activeUsers,        // 今日有 token 活跃的用户数
        long overQuotaUsers,     // 今日超额用户数
        long quota,              // 当前统一限额
        List<String> dates,      // 最近 7 天日期（用于柱状图 X 轴）
        Map<String, List<Long>> models, // 各模型每日消耗（不同模型不同颜色）
        List<Long> totals        // 每日合计消耗
) {
}
