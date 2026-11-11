package com.campus.activity.config;

import com.campus.activity.security.JsonAccessDeniedHandler;
import com.campus.activity.security.JsonAuthenticationEntryPoint;
import com.campus.activity.security.JwtAuthenticationFilter;
import com.campus.activity.security.JwtUtil;
import com.campus.activity.security.SysUserDetailsService;
import com.campus.activity.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置的唯一入口：前后端分离 + 无状态 JWT。
 * <p>
 * 路径规则只做“粗粒度”的守门（后台、组织者、文件必须登录），
 * 具体的业务登录校验交给 Controller 上的方法级 @PreAuthorize，
 * 这样公开接口（如活动列表、活动详情）也能携带 token 拿到个性化字段。
 * <p>
 * 认证与授权失败统一走 JSON：前端的拦截器只认 code 10001 / 10002，
 * 不能出现 302 跳登录页或 HTML 错误页。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final SysUserDetailsService sysUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JsonAuthenticationEntryPoint authenticationEntryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // 前后端分离，浏览器不会自动带 Authorization 头，没有 CSRF 攻击面
                .csrf(csrf -> csrf.disable())
                // 空 lambda：让 Security 去拾取容器里的 CorsConfigurationSource
                .cors(Customizer.withDefaults())
                // 关掉会抢在授权之前返回 302 / 401 的默认入口
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                // 无状态：不创建 Session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 预检请求不带 token，不放行就永远 401
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // /error 一律放行：MVC 的参数校验失败会 forward 到它，拦下来会把 400 覆盖成 401
                        .requestMatchers(
                                "/api/ping",
                                "/api/app/auth/login",
                                "/api/app/auth/sms/**",
                                "/api/admin/auth/login",
                                "/error",
                                "/uploads/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**").permitAll()
                        // 后台管理只放 REVIEWER / ADMIN
                        .requestMatchers("/api/admin/**").hasAnyRole("REVIEWER", "ADMIN")
                        // 组织者中心只放 ORGANIZER / ADMIN
                        .requestMatchers("/api/org/**").hasAnyRole("ORGANIZER", "ADMIN")
                        // 文件上传接口需要登录，具体业务校验在 Controller 方法上
                        .requestMatchers("/api/files/**").authenticated()
                        // 其余公开接口放行，登录校验靠方法级 @PreAuthorize
                        .anyRequest().permitAll())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // 放在用户名密码过滤器之前：先解析 JWT，授权过滤器才能看到身份
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, tokenBlacklistService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 后台账号密码登录用：DaoAuthenticationProvider 负责查库与密码校验。
     * 密码错误抛 BadCredentialsException，账号禁用抛 DisabledException。
     */
    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(sysUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** 密码统一用 BCrypt 校验，与 sys_user.password_hash 的 $2y$ 密文兼容 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 跨域统一在 Security 层处理，WebMvcConfig 里不再重复配置。
     * 本地调试放开所有来源，生产环境由 Nginx 同域反向代理，不需要跨域。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
