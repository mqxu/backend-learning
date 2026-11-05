package top.mqxu.zhihu.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.mqxu.zhihu.common.UserContext;

/**
 * 认证拦截器。
 *
 * <p>它只做一件事：有 token 就解析出来放进 UserContext，没有就放行。
 * 至于"这个接口要不要登录"，由业务层调 {@code UserContext.requireUserId()} 决定。
 * 这样列表、详情这类"登录与否都能看"的接口不用在拦截器里维护一份白名单。</p>
 *
 * @author mqxu
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            Long userId = jwtService.parseUserId(header.substring(BEARER.length()));
            if (userId != null) {
                UserContext.set(userId);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 不清理的话，线程被复用时会把这个用户带给下一个请求
        UserContext.clear();
    }
}
