package top.mqxu.web.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 令牌校验拦截器：preHandle 返回 false 就到此为止，Controller 与 postHandle 都不会执行
 *
 * @author mqxu
 */
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Token";
    private static final String EXPECTED = "demo-token";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = request.getHeader(HEADER);
        if (EXPECTED.equals(token)) {
            return true;
        }
        log.warn("TokenInterceptor 拦住请求：{} {}，{} 不合法", request.getMethod(), request.getRequestURI(), HEADER);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                "{\"code\":401,\"message\":\"缺少或错误的 X-Token\",\"data\":null}".getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
