package top.mqxu.redis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：跨域放行与登录态拦截。
 *
 * @author mqxu
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 开发时 Vite 的端口可能被占用后顺延，用模式匹配比写死端口方便
                .allowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                // 只有 /user/** 代表"登录后才有的接口"，其余都是可以直接观察的演示接口。
                // 若把演示接口也拦住，看缓存命中效果前还得先登录，实验做起来很别扭。
                .excludePathPatterns("/sms/**", "/redis/**", "/cache/**",
                        "/lock/**", "/limit/**", "/rank/**", "/error");
    }
}
