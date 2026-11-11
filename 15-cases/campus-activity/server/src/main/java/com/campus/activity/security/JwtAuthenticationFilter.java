package com.campus.activity.security;

import com.campus.activity.common.LoginUser;
import com.campus.activity.common.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 每个请求解析一次 Authorization: Bearer xxx。
 * <p>
 * 验签通过且未被拉黑时，同时写入两个上下文：
 * SecurityContext（Security 自己的判断依据）与 UserContext（业务代码沿用的 ThreadLocal）。
 * 两者必须保持一致，所以放在同一个过滤器里写、在 finally 里清 UserContext，
 * 避免线程池复用导致身份串号。
 * <p>
 * token 无效不在这里直接返回 401，只清空上下文以匿名身份继续，
 * 由后面的授权层统一拒绝：路径规则走 ExceptionTranslationFilter，
 * 方法级 @PreAuthorize 走 SecurityExceptionAdvice，两边输出同样的 JSON。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            authenticate(request);
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return;
        }

        JwtUtil.TokenClaims claims = jwtUtil.parse(header.substring(BEARER_PREFIX.length()).trim());
        if (claims == null || tokenBlacklistService.isBlocked(claims.jti())) {
            SecurityContextHolder.clearContext();
            return;
        }

        LoginUser user = claims.user();
        UserContext.set(user);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(user.id()), null, authorities);
        // 新建 context 再整体塞进去，不要改已有的那个
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
