package org.microsoft.qintelipass.configs;

import org.microsoft.qintelipass.interceptors.UserStatusInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Autowired
    private UserStatusInterceptor userStatusInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userStatusInterceptor)
                .addPathPatterns("/api/**")  // 拦截所有API请求
                .excludePathPatterns(                 // 排除不需要拦截的路径
                        "/api/v1/portal/login",  // 登录接口
                        "/api/v1/portal/register", // 注册接口（如果有）
                        "/api/admin/users"           // 获取用户列表（管理员接口）
                );
    }
}
