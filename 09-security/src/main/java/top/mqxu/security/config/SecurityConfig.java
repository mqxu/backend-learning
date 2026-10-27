package top.mqxu.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import top.mqxu.security.security.JwtAuthenticationFilter;
import top.mqxu.security.security.JwtTokenService;
import top.mqxu.security.security.RestAccessDeniedHandler;
import top.mqxu.security.security.RestAuthenticationEntryPoint;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 安全配置：过滤链、认证管理器、密码编码器
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
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 规则按从具体到宽泛排列，anyRequest 必须放最后
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/public/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
