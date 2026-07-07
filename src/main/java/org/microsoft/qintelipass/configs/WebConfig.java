package org.microsoft.qintelipass.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC 配置
 * - CORS 跨域支持（开发时 Vite dev server 与 Spring Boot 不同端口）
 * - 静态资源映射 + SPA 前端路由 fallback
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                    "http://localhost:5173",
                    "http://localhost:7510",
                    "http://1.14.203.243"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * SPA fallback：静态资源找不到时返回 index.html
     * 这样 Vue Router 的客户端路由才能正常工作
     * API 路径由 Controller 处理，不会走到这里
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = super.getResource(resourcePath, location);
                        if (resource == null) {
                            // SPA fallback：返回 index.html
                            return super.getResource("index.html", location);
                        }
                        return resource;
                    }
                });
    }
}
