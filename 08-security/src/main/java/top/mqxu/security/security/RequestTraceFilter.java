package top.mqxu.security.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个请求打一行访问日志，顺带演示怎么在过滤器里读身份。
 * <p>
 * 插在 AuthorizationFilter 之前，此时 JWT 过滤器已经写过上下文，这里能读到真实身份；
 * 插得比认证过滤器还早的话，读到的永远是 anonymous。
 *
 * @author mqxu
 */
@Slf4j
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        // 此刻 JWT 过滤器已经跑过，能读到真实身份；插得更早的话这里只会是 anonymous
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} -> {} ({} ms) user={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    System.currentTimeMillis() - start,
                    auth == null ? "anonymous" : auth.getName());
        }
    }
}
