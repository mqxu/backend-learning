package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 匿名访问受保护资源时被 ExceptionTranslationFilter 调用。
 * <p>
 * 默认实现会按 Accept 协商：浏览器 302 跳登录页，其余 401 + WWW-Authenticate。
 * 前后端分离下两种都不能用，一律返回 401 + JSON。
 *
 * @author mqxu
 */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY = """
            {"code":401,"message":"未登录或登录已过期","data":null}""";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 不写 WWW-Authenticate，否则浏览器会弹出原生账号密码框
        response.getWriter().write(BODY);
    }
}
