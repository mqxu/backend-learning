package top.mqxu.web.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 密钥校验过滤器：只挂在 /demo/secure 上，校验不过直接写响应，不再往下走。
 * 用来演示过滤器的短路能力。由 FilterConfig 用 FilterRegistrationBean 注册。
 *
 * @author mqxu
 */
@Slf4j
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";
    private static final String EXPECTED = "demo-key";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (!EXPECTED.equals(key)) {
            log.warn("ApiKeyFilter 拦住请求：{} {}，{} 不合法", request.getMethod(), request.getRequestURI(), HEADER);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"缺少或错误的 X-Api-Key\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
