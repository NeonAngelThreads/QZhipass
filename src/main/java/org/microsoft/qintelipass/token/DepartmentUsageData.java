package org.microsoft.qintelipass.token;

import java.util.List;

/**
 * 管理员视角：按部门统计的当日 token 使用情况
 */
public record DepartmentUsageData(
        String date,
        List<DepartmentRow> departments,
        List<UserUsageRow> users
) {

    /** 部门维度汇总 */
    public record DepartmentRow(
            String department,
            long userCount,
            long totalTokens,
            long overQuotaCount
    ) {
    }

    /** 员工维度明细 */
    public record UserUsageRow(
            Long id,
            String name,
            String department,
            long totalTokens,
            long quota,
            boolean overQuota
    ) {
    }
}
