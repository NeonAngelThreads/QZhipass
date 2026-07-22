package org.microsoft.qintelipass.enums;

public enum UserStatus {
    NORMAL,    // 正常
    FROZEN,    // 冻结
    CANCELLED  // 已注销（替换原来的 DEACTIVATED，与 schema 保持一致）
}
