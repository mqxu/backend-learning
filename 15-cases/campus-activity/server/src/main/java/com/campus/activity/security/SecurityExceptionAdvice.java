package com.campus.activity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * 方法级 @PreAuthorize 拒绝时的兜底。
 * <p>
 * 和路径规则不同，注解是在 Controller 调用栈里生效的，抛出的 AccessDeniedException
 * 先被 Spring MVC 的异常处理接住，根本走不到 ExceptionTranslationFilter。
 * 本项目 common/GlobalExceptionHandler 有 @ExceptionHandler(Exception.class) 兜底，
 * 不在这里按优先级抢先处理，就会把 401/403 变成 500。
 * <p>
 * 因此按“匿名 → 401 + 10001，已登录 → 403 + 10002”分别复用两个既有处理器，
 * 保证方法级与路径级的响应体完全一致。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SecurityExceptionAdvice {

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            authenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException("未登录或登录已过期", e));
            return;
        }
        accessDeniedHandler.handle(request, response, e);
    }
}
