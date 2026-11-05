package top.mqxu.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import top.mqxu.security.security.JwtAuthenticationFilter;
import top.mqxu.security.security.JwtTokenService;
import top.mqxu.security.security.RestAccessDeniedHandler;
import top.mqxu.security.security.RestAuthenticationEntryPoint;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 安全配置：过滤链、认证管理器、密码编码器、跨域
 * <p>
 * Security 7 只认组件式配置，WebSecurityConfigurerAdapter 早已移除。
 **/
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * BCrypt 自带随机盐，同一密码两次加密结果不同，因此只能 matches 不能字符串比较
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 登录接口用这个 AuthenticationManager 校验账号密码，
     * 密码比对、账号禁用检查都由 DaoAuthenticationProvider 完成
     * <p>
     * Provider 在这里内部构造，不再单独暴露成 Bean：
     * 容器里出现 AuthenticationProvider Bean 时，Security 会认为认证管理器已有主，
     * 启动日志里会多一条 UserDetailsService 不被用于自动配置的警告。
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                      PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * 前后端分离的跨域配置。前端跑在 5173，后端在 8009，端口不同就算跨域。
     * <p>
     * 令牌走请求头而不是 cookie，所以不需要 allowCredentials。
     * 如果开发时用 Vite 的 proxy 转发，这个配置不是必需的，但上线分离部署时必须有。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService,
                                                   UserDetailsService userDetailsService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenService, userDetailsService);
        http
                // 纯 Bearer 令牌、无 cookie 会话，可以关掉 CSRF；一旦改回 session 认证必须重新打开
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 规则按从具体到宽泛排列，anyRequest 必须放最后
                .authorizeHttpRequests(auth -> auth
                        // 登录接口必须放行，否则连令牌都拿不到
                        .requestMatchers("/auth/login", "/public/**", "/error").permitAll()
                        // 管理接口整组限管理员，第二道防线在 Controller 的注解上
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 任务接口只要求登录，具体谁能做什么由方法上的权限点决定
                        .requestMatchers("/api/tasks/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
