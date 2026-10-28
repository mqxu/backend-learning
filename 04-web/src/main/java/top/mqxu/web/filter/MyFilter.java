package top.mqxu.web.filter;

import jakarta.servlet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description MyFilter
 **/
@Slf4j
@Component
public class MyFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("MyFilter 过滤器初始化");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info("执行 MyFilter 的过滤逻辑");
        chain.doFilter(request, response);
        log.info("接口访问完毕，回到 MyFilter");
    }

    @Override
    public void destroy() {
        log.info("MyFilter 过滤器销毁");
    }
}
