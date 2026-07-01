package org.microsoft.qintelipass.utils;

import java.util.regex.Pattern;

/**
 * 密码验证工具类
 * 规则：必须包含大写字母、小写字母、特殊字符、数字，长度>=8
 */
public class PasswordValidator {

    // 至少1个大写字母
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    // 至少1个小写字母
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    // 至少1个数字
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    // 至少1个特殊字符
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9]");
    // 最小长度
    private static final int MIN_LENGTH = 8;

    /**
     * 校验密码是否符合规格
     * @return null 表示通过；否则返回错误描述
     */
    public static String validate(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }
        if (password.length() < MIN_LENGTH) {
            return "密码长度必须不少于8个字符";
        }
        if (!UPPER.matcher(password).find()) {
            return "密码必须包含至少一个大写字母";
        }
        if (!LOWER.matcher(password).find()) {
            return "密码必须包含至少一个小写字母";
        }
        if (!DIGIT.matcher(password).find()) {
            return "密码必须包含至少一个数字";
        }
        if (!SPECIAL.matcher(password).find()) {
            return "密码必须包含至少一个特殊字符（如 !@#$%^&* 等）";
        }
        return null; // 验证通过
    }
}
