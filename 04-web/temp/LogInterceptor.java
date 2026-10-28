package top.mqxu.web.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 日志拦截器：统计接口耗时（Spring MVC 层）
 *
 * @author mqxu
 */
@Slf4j
@Component
public class LogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long start = (Long) request.getAttribute("startTime");
        if (start != null) {
            log.info("拦截器统计：{} {} 耗时 {}ms", request.getMethod(), request.getRequestURI(),
                    System.currentTimeMillis() - start);
        }
    }
}
