package top.mqxu.web.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 回调追踪拦截器：把三个回调的调用顺序打出来，用来观察异常与短路时的差异
 *
 * @author mqxu
 */
@Slf4j
@Component
public class CallbackTraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info("preHandle：{} {}，handler = {}", request.getMethod(), request.getRequestURI(),
                handler.getClass().getSimpleName());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        log.info("postHandle：{} {}，此时响应还没写出去", request.getMethod(), request.getRequestURI());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        log.info("afterCompletion：{} {}，异常 = {}", request.getMethod(), request.getRequestURI(),
                ex == null ? "无" : ex.getClass().getSimpleName());
    }
}
