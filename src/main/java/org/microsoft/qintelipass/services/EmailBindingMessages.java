package org.microsoft.qintelipass.services;

public final class EmailBindingMessages {
    public static final String EMAIL_UNAVAILABLE = "该邮箱地址不可用";
    public static final String EMAIL_OCCUPIED = "该邮箱已被其他账号绑定";
    public static final String EMAIL_ALREADY_BOUND = "当前账号已绑定邮箱，无需重复操作";
    public static final String CODE_EXPIRED = "验证码已过期，请重新获取";
    public static final String CODE_INCORRECT = "验证码错误";
    public static final String CODE_RATE_LIMITED = "验证码发送过于频繁，请稍后再试";
    public static final String CODE_DELIVERY_FAILED = "验证码发送失败，请稍后重试";
    public static final String CACHE_UNAVAILABLE = "验证码服务暂时不可用，请稍后重试";
    public static final String PERSISTENCE_FAILED = "邮箱绑定失败，请稍后重试";

    private EmailBindingMessages() {
    }
}
