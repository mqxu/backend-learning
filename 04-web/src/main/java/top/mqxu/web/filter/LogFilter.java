package top.mqxu.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description LogFilter
 **/
@Slf4j
@Component
public class LogFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("LogFilter 过滤器初始化");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info("执行 LogFilter 的过滤逻辑");
        // 获取客户端的请求方法和地址
        HttpServletRequest req = (HttpServletRequest) request;
        String method = req.getMethod();
        String uri = req.getRequestURI();
        // 获取所有请求参数（含查询参数和表单参数）
        Map<String, String[]> parameterMap = req.getParameterMap();
        String params = parameterMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));


        log.info("请求方法是：{}，请求地址是：{}，请求参数是：{}，请求时间是：{}",
                method, uri, params, LocalDateTime.now());
        // 执行完本过滤器后，将请求放行到下一个过滤器
        chain.doFilter(request, response);
        log.info("接口访问完毕，回到 LogFilter");
    }

    @Override
    public void destroy() {
        log.info("LogFilter 过滤器销毁");
    }
}
