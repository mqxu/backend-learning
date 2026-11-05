package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 已登录但权限不够时被调用，返回 403 + JSON。
 * <p>
 * 另一个 403 来源是 CsrfFilter（无状态配置下已关闭），两者状态码一样，
 * 排查时看 TRACE 日志里有没有 Invalid CSRF token 才能分清。
 *
 * @author mqxu
 */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final String BODY = """
            {"code":403,"message":"没有访问权限","data":null}""";

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
