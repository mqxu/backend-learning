package top.mqxu.web.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 注解方式注册的过滤器：靠启动类上的 @ServletComponentScan 扫描到，
 * 实例由 Tomcat 创建，不是 Spring Bean，所以这里不能 @Autowired。
 *
 * @author mqxu
 */
@Slf4j
//@WebFilter(urlPatterns = "/demo/annotation/*", filterName = "annotationDemoFilter")
public class AnnotationDemoFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        log.info("@WebFilter 生效：{} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
        chain.doFilter(request, response);
    }
}
