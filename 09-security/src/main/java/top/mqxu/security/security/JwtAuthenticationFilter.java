package top.mqxu.security.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 从 Authorization: Bearer 头解析令牌，校验通过后把认证信息放进 SecurityContext
 * <p>
 * 这个类不是 Spring Bean，由 SecurityConfig 在装配过滤链时 new 出来。
 * 若加上 @Component，Spring Boot 会把它同时注册成 Servlet 过滤器，一次请求会跑两遍。
 **/
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenService tokenService, UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 令牌无效时不在这里写响应，只清空上下文放行。
     * 后续的授权判断会拒绝匿名请求，由 RestAuthenticationEntryPoint 统一返回 401，
     * 避免同一个错误出现两种响应格式。
     */
    private void authenticate(HttpServletRequest request, String token) {
        try {
            String username = tokenService.parseUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();
            log.debug("令牌校验未通过：{}", e.getMessage());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
