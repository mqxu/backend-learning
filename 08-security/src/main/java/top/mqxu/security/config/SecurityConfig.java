package top.mqxu.security.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import top.mqxu.security.security.JsonAccessDeniedHandler;
import top.mqxu.security.security.JsonAuthenticationEntryPoint;
import top.mqxu.security.security.JwtAuthenticationFilter;
import top.mqxu.security.security.JwtService;
import top.mqxu.security.security.RequestTraceFilter;
import top.mqxu.security.sms.SmsCodeAuthenticationProvider;
import top.mqxu.security.sms.SmsCodeService;

/**
 * 安全配置的唯一入口。
 * <p>
 * 教程是“一条链逐步演进”，这里改成“五条链各管一段路径”，用 securityMatcher 隔开。
 * 好处是五个阶段可以在同一个进程里同时跑、同时验证，不用改配置重启：
 * <p>
 * /stage1/**  全拦截，只有 Basic 认证
 * /stage2/**  加了白名单，公开接口与 GET 放行
 * /stage3/**  加了角色与权限点，hasRole / hasAnyRole / hasAllRoles / hasAuthority
 * /stage4/**  表单登录 + Session + CSRF，传统 Web 那一套
 * 其余        前后端分离：无状态 JWT + 短信登录 + 统一 JSON + CORS
 * <p>
 * 规则匹配是从上往下、命中即停，所以 @Order 小的链一定要写在前面。
 *
 * @author mqxu
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // ---------------------------------------------------------------- 阶段一：全拦截

    @Bean
    @Order(1)
    public SecurityFilterChain stage1FilterChain(HttpSecurity http,
                                                 AuthenticationManager authenticationManager) {
        http
                .securityMatcher("/stage1/**")
                .authorizeHttpRequests(auth -> auth
                        // 就这一句：任何请求都必须已认证
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // 统一成 JSON：默认实现会按 Accept 协商，浏览器拿到 302 或 HTML 登录框
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()))
                .authenticationManager(authenticationManager);
        return http.build();
    }

    // ---------------------------------------------------------------- 阶段二：放行白名单

    @Bean
    @Order(2)
    public SecurityFilterChain stage2FilterChain(HttpSecurity http,
                                                 AuthenticationManager authenticationManager) {
        http
                .securityMatcher("/stage2/**")
                .authorizeHttpRequests(auth -> auth
                        // 公开接口
                        .requestMatchers("/stage2/public/**").permitAll()
                        // 路径匹配可以带方法：GET 放行，POST / PUT / DELETE 仍要登录
                        .requestMatchers(HttpMethod.GET, "/stage2/articles/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()))
                .authenticationManager(authenticationManager);
        return http.build();
    }

    // ---------------------------------------------------------------- 阶段三：角色与权限点

    @Bean
    @Order(3)
    public SecurityFilterChain stage3FilterChain(HttpSecurity http,
                                                 AuthenticationManager authenticationManager) {
        http
                .securityMatcher("/stage3/**")
                .authorizeHttpRequests(auth -> auth
                        // 越具体越靠前，越宽泛越靠后，anyRequest() 永远最后
                        .requestMatchers("/stage3/admin/**").hasRole("ADMIN")
                        .requestMatchers("/stage3/ops/**").hasAnyRole("ADMIN", "AUDITOR")
                        // Security 7 新增：必须同时具备两个角色
                        .requestMatchers("/stage3/audit/**").hasAllRoles("ADMIN", "AUDITOR")
                        // 细粒度权限点，不带 ROLE_ 前缀
                        .requestMatchers(HttpMethod.DELETE, "/stage3/articles/**")
                        .hasAuthority("ARTICLE_DELETE")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()))
                .authenticationManager(authenticationManager);
        return http.build();
    }

    // ---------------------------------------------------------------- 阶段四：表单登录

    @Bean
    @Order(4)
    public SecurityFilterChain stage4FilterChain(HttpSecurity http,
                                                 AuthenticationManager authenticationManager) {
        http
                .securityMatcher("/stage4/**")
                .authorizeHttpRequests(auth -> auth
                        // 登录页、样式与取 token 的端点都必须放行，否则页面还没登录就调不通
                        .requestMatchers("/stage4/login.html", "/stage4/login.css",
                                "/stage4/csrf-token").permitAll()
                        // loginProcessingUrl 与 logoutUrl 都要放行，否则被自己的认证流程拦住
                        .requestMatchers("/stage4/login", "/stage4/logout").permitAll()
                        .requestMatchers("/stage4/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        // 登录页用静态资源，避开“loginPage 与 loginProcessingUrl 同址”的死循环
                        .loginPage("/stage4/login.html")
                        .loginProcessingUrl("/stage4/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/stage4/me", false)
                        .failureUrl("/stage4/login.html?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/stage4/logout")
                        .logoutSuccessUrl("/stage4/login.html?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                // 表单场景要用不做 XOR 掩码的 handler：Cookie 里是 36 字符裸 UUID，
                // 默认的 XOR 版会把 _csrf 属性掩码成 96 字符，两边永远对不上
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authenticationManager(authenticationManager);
        return http.build();
    }

    // ---------------------------------------------------------------- 阶段五：前后端分离

    @Bean
    @Order(5)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              AuthenticationManager authenticationManager,
                                              JwtService jwtService,
                                              RequestTraceFilter requestTraceFilter) {
        http
                .authorizeHttpRequests(auth -> auth
                        // 预检请求不带 token，不放行就永远 401
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // /error 一律放行：Spring MVC 的参数校验失败会 forward 到它，
                        // 拦下来会把 400 覆盖成 401
                        .requestMatchers("/", "/index.html", "/error",
                                "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/sms/**",
                                "/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/articles/**")
                        .hasAuthority("ARTICLE_DELETE")
                        .anyRequest().authenticated())

                // 无状态：不再创建 Session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 关掉会抢在授权之前返回 302 的三个过滤器
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                // 浏览器不会自动带 Authorization 头，没有 CSRF 攻击面
                .csrf(csrf -> csrf.disable())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()))

                // 空 lambda：让 Security 去拾取容器里的 CorsConfigurationSource
                .cors(Customizer.withDefaults())

                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class)
                // 位置决定能看到什么：放在授权之前才能读到已解析的身份
                .addFilterBefore(requestTraceFilter, AuthorizationFilter.class)

                .authenticationManager(authenticationManager);
        return http.build();
    }

    // ---------------------------------------------------------------- 认证与密码

    /**
     * 两个 Provider 挂在同一个 Manager 上，顺序无所谓，靠 supports() 区分。
     */
    @Bean
    public AuthenticationManager authenticationManager(
            @Qualifier("dbUserDetailsService") UserDetailsService dbUserDetailsService,
            @Qualifier("phoneUserDetailsService") UserDetailsService phoneUserDetailsService,
            PasswordEncoder passwordEncoder,
            SmsCodeService smsCodeService) {

        DaoAuthenticationProvider passwordProvider =
                new DaoAuthenticationProvider(dbUserDetailsService);
        passwordProvider.setPasswordEncoder(passwordEncoder);

        SmsCodeAuthenticationProvider smsProvider =
                new SmsCodeAuthenticationProvider(smsCodeService, phoneUserDetailsService);

        return new ProviderManager(passwordProvider, smsProvider);
    }

    /**
     * 委托式编码器：密文前缀决定用哪种算法校验，{bcrypt} 走 BCrypt，{noop} 走明文。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * RequestTraceFilter 是 @Component，Spring Boot 会把它当成普通 Filter 再注册进 Servlet 容器，
     * 结果同一个请求进两次 Security 链。用这个 Bean 关掉自动注册，只留 Security 链里那一份。
     */
    @Bean
    public FilterRegistrationBean<RequestTraceFilter> requestTraceFilterRegistration(
            RequestTraceFilter filter) {
        FilterRegistrationBean<RequestTraceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
