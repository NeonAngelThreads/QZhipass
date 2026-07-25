package org.microsoft.qintelipass.configs;

import org.microsoft.qintelipass.interceptors.UserStatusInterceptor;
import org.microsoft.qintelipass.token.TokenQuotaInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Autowired
    private UserStatusInterceptor userStatusInterceptor;

    @Autowired
    private TokenQuotaInterceptor tokenQuotaInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userStatusInterceptor)
                .addPathPatterns("/api/**")  // 拦截所有API请求
                .excludePathPatterns(                 // 排除不需要拦截的路径
                        "/api/v1/portal/login",       // 登录接口
                        "/api/v1/portal/register",    // 注册接口
                        "/api/admin/users",           // 管理员用户列表
                        "/api/admin/token/**",        // Token 管理后台
                        "/api/v1/admin/token/**",     // Token 前端适配
                        "/api/user/token",            // 用户 Token 查询
                        "/api/v1/user/token/**",      // 用户 Token 前端适配
                        "/api/v1/chat/**"             // 聊天检测接口
                );

        // Token 配额拦截器：聊天请求时服务端校验是否超额
        registry.addInterceptor(tokenQuotaInterceptor)
                .addPathPatterns("/v1/chat/**")
                .excludePathPatterns("/v1/chat/check");
    }
}
