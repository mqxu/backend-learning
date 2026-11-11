package com.campus.activity.security;

import com.campus.activity.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 匿名访问受保护资源时被 ExceptionTranslationFilter 调用。
 * <p>
 * 不走 Jackson（Boot 4 已切到 Jackson 3），直接写固定结构，避免版本耦合；
 * 也不写 WWW-Authenticate，否则浏览器会弹原生账号密码框。
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        writeBody(response, ResultCode.UNAUTHORIZED);
    }

    /** 401 与 403 的响应体只差一个错误码，抽出来给 SecurityExceptionAdvice 复用 */
    static void writeBody(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"code\":" + resultCode.getCode()
                + ",\"message\":\"" + resultCode.getMessage() + "\",\"data\":null}";
        response.getWriter().write(body);
    }
}
