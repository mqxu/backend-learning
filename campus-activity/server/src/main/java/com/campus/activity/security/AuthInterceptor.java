package com.campus.activity.security;

import com.campus.activity.common.LoginUser;
import com.campus.activity.common.ResultCode;
import com.campus.activity.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 解析 JWT 并校验注解声明的登录与角色要求。
 * 未标注 @RequireLogin 的接口也允许携带 token，用于返回“我的报名状态”等个性化字段。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            LoginUser user = jwtUtil.parse(header.substring(BEARER.length()).trim());
            if (user != null) {
                UserContext.set(user);
            }
        }

        boolean needLogin = handlerMethod.hasMethodAnnotation(RequireLogin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireLogin.class);
        LoginUser current = UserContext.get();

        if (needLogin && current == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED);
            return false;
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            if (current == null) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED);
                return false;
            }
            boolean allowed = Arrays.stream(requireRole.value()).anyMatch(r -> r.equals(current.role()));
            if (!allowed) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN);
                return false;
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 不走 Jackson（Spring Boot 4 已切到 Jackson 3），直接写固定结构，避免版本耦合 */
    private void writeError(HttpServletResponse response, int httpStatus, ResultCode code) throws Exception {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"code\":" + code.getCode() + ",\"message\":\"" + code.getMessage() + "\",\"data\":null}";
        response.getWriter().write(body);
    }
}
