package top.mqxu.web.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.mqxu.web.interceptor.TimeStatInterceptor;

/**
 * Web 配置
 *
 * @author mqxu
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final TimeStatInterceptor timeStatInterceptor;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册耗时统计拦截器
        registry.addInterceptor(timeStatInterceptor)
                // 拦截所有路径
                .addPathPatterns("/**")
                // 排除静态资源和错误页
                .excludePathPatterns("/static/**", "/error")
                // 执行顺序（值越小越先执行）
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
