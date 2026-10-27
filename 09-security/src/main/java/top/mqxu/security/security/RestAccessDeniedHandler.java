package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.common.Result;

import java.io.IOException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 已认证但角色/权限不足时返回 403
 **/
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final RestErrorWriter errorWriter;

    public RestAccessDeniedHandler(RestErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("权限不足访问 {}：{}", request.getRequestURI(), accessDeniedException.getMessage());
        errorWriter.write(response, HttpStatus.FORBIDDEN, Result.fail(ErrorCode.FORBIDDEN));
    }
}
