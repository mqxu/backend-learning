package top.mqxu.security.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import top.mqxu.security.common.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 在过滤器层写 JSON 响应。认证与授权失败发生在进 Controller 之前，
 * 全局异常处理器接不到，只能在这里手动拼响应体
 **/
@Component
public class RestErrorWriter {

    private final ObjectMapper objectMapper;

    public RestErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, Result<Void> body) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
