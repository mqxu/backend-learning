package top.mqxu.web.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求耗时过滤器（Servlet 层，比拦截器更早触发）
 *
 * @author mqxu
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestTimeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();
        chain.doFilter(request, response);
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        log.info("过滤器统计：{} {} 耗时 {}ms", httpRequest.getMethod(), httpRequest.getRequestURI(),
                System.currentTimeMillis() - start);
    }
}
