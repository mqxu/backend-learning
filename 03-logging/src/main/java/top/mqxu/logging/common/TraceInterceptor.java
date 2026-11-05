package top.mqxu.logging.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 为每次请求生成 traceId 写入 MDC，并回写到响应头，方便把日志和请求对上。
 *
 * @author mqxu
 */
@Component
public class TraceInterceptor implements HandlerInterceptor {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String TRACE_KEY = "traceId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        // 上游已经带了 traceId 就沿用，保证一次调用链路只有一个编号
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(TRACE_KEY, traceId);
        // 回写响应头，调用方拿到号就能去日志里 grep
        response.setHeader(TRACE_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        // 请求结束必须清理，避免线程池复用线程导致编号串到下一个请求
        MDC.clear();
    }
}
