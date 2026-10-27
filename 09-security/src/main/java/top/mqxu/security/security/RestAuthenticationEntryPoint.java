package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.common.Result;

import java.io.IOException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 未认证访问受保护资源时返回 401。默认行为是重定向到登录页，纯 API 场景要改掉
 **/
@Slf4j
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final RestErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(RestErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("未认证访问 {}：{}", request.getRequestURI(), authException.getMessage());
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, Result.fail(ErrorCode.UNAUTHORIZED));
    }
}
