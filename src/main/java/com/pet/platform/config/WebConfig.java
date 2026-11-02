package com.pet.platform.config;

import com.pet.platform.security.AdminAuthInterceptor;
import com.pet.platform.security.UserAuthInterceptor;
import com.pet.platform.storage.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final UserAuthInterceptor userAuthInterceptor;
    private final UploadProperties uploadProperties;

    /**
     * 允许的跨域来源，逗号分隔；{@code *} 表示不限制（默认值，兼容本地任意端口调试）。
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/staff/login");

        // 小程序端：登录、门店/分类/服务浏览无需登录
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/shop/status",
                        "/user/category/**",
                        "/user/service-item/**",
                        "/user/package/**",
                        "/user/schedule/available"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        CorsRegistration registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        if (origins.length == 0 || (origins.length == 1 && "*".equals(origins[0]))) {
            // 通配模式：本项目的鉴权走请求头（token / authentication）而非 Cookie，
            // 浏览器不会自动带凭证，因此风险低于 Cookie 场景。
            // 生产建议用环境变量 CORS_ALLOWED_ORIGINS 收紧为具体域名。
            registration.allowedOriginPatterns("*");
        } else {
            registration.allowedOrigins(origins);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolute = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolute + "/");
    }
}
