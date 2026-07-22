package org.microsoft.qintelipass.request;

import lombok.Data;

/**
 * 创建账户请求DTO
 * 包含：姓名、部门、邮箱、手机号码、微信、密码、确认密码
 */
@Data
public class CreateAccountRequest {
    /** 姓名 */
    private String name;
    /** 所在部门 */
    private String department;
    /** 邮箱 */
    private String email;
    /** 手机号码 */
    private String phone;
    /** 微信号 */
    private String wechat;
    /** 密码 */
    private String password;
    /** 确认密码（二次验证） */
    private String confirmPassword;
}