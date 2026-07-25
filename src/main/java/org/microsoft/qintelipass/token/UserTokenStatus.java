package org.microsoft.qintelipass.token;

/**
 * 某用户当日的 token 限额与使用状态
 */
public record UserTokenStatus(
        Long userId,
        long quota,
        long used,
        long remaining,
        boolean overQuota
) {
}
