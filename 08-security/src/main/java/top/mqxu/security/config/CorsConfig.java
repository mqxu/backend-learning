package top.mqxu.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * 跨域配置。
 * <p>
 * 必须挂在 Security 层（.cors()），不能只写 WebMvcConfigurer 或 @CrossOrigin：
 * Security 的过滤器链在最外层，请求在 AuthorizationFilter 就被 401 拦下，走不到 MVC，
 * 401 响应上也就不会有 CORS 头，浏览器会把整个响应丢掉，前端只看到 TypeError: Failed to fetch。
 *
 * @author mqxu
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();

        // 用 OriginPatterns 而不是 Origins：它支持通配写法，且回显具体来源而不是返回 *
        config.setAllowedOriginPatterns(properties.getCors().getAllowedOrigins());

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 要发 Authorization 就必须在这里列出来，少一个前端就发不出去
        config.setAllowedHeaders(List.of("Authorization", "Content-Type",
                "X-Requested-With", "X-XSRF-TOKEN"));

        // 不写这两个，JS 读不到响应里的 Authorization 与文件名
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

        // token 放请求头时给 false；改成 Cookie 传 token 时必须 true
        config.setAllowCredentials(false);

        // 预检结果缓存一小时，少一半请求
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
