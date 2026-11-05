package top.mqxu.web.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.mqxu.web.interceptor.CallbackTraceInterceptor;
import top.mqxu.web.interceptor.TimeStatInterceptor;
import top.mqxu.web.interceptor.TokenInterceptor;

/**
 * Web 配置
 *
 * @author mqxu
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final TimeStatInterceptor timeStatInterceptor;
    private final CallbackTraceInterceptor callbackTraceInterceptor;
    private final TokenInterceptor tokenInterceptor;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 全局耗时统计，排除静态资源和错误页
        registry.addInterceptor(timeStatInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/error")
                .order(1);

        // 只挂在演示路径上，用来观察三个回调的顺序
        registry.addInterceptor(callbackTraceInterceptor)
                .addPathPatterns("/demo/callback")
                .order(2);

        // 只挂在演示路径上，校验不过直接短路
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/demo/token")
                .order(3);
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
