package top.mqxu.security.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 每个请求读一次 Authorization: Bearer xxx，验签通过就把身份写进上下文。
 * <p>
 * 验签失败并不直接返回 401，只是清空上下文让请求以匿名身份继续，
 * 由后面的 AuthorizationFilter 统一拒绝。这样错误响应只有一份实现。
 *
 * @author mqxu
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 没有凭证：直接放行，交给授权层拒绝
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()));
            List<?> raw = claims.get(JwtService.CLAIM_AUTHORITIES, List.class);
            List<GrantedAuthority> authorities = raw == null
                    ? List.of()
                    : raw.stream()
                    .map(String::valueOf)
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                    .toList();

            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    claims.getSubject(), null, authorities);

            // 新建 context 再整体塞进去，不要改已有的那个
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

        } catch (JwtException | IllegalArgumentException e) {
            // 签名错、过期、格式错：当匿名处理，后面自然 401
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
