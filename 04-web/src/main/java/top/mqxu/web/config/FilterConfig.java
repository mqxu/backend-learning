package top.mqxu.web.config;

import lombok.AllArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.mqxu.web.filter.LogFilter;
import top.mqxu.web.filter.MyFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description FilterConfig
 **/
@Configuration
@AllArgsConstructor
public class FilterConfig {
    private final LogFilter logFilter;
    private final MyFilter myFilter;

    @Bean
    public FilterRegistrationBean<LogFilter> logFilterFilterRegistrationBean() {
        FilterRegistrationBean<LogFilter> registration = new FilterRegistrationBean<>();
        // 设置过滤器实例
        registration.setFilter(logFilter);
        // 设置拦截路径（/* 为匹配所有请求）
        List<String> urlPatterns = new ArrayList<>();
        urlPatterns.add("/*");
        registration.setUrlPatterns(urlPatterns);
        // 设置执行顺序（值越小越先执行）
        //registration.setOrder(2);
        // 排除静态资源（可选）
        registration.addInitParameter("exclusions", "*.js,*.css,*.png");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<MyFilter> myFilterFilterRegistrationBean() {
        FilterRegistrationBean<MyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(myFilter);
        List<String> urlPatterns = new ArrayList<>();
        urlPatterns.add("/*");
        registration.setUrlPatterns(urlPatterns);
        //registration.setOrder(1);
        registration.addInitParameter("exclusions", "*.js,*.css,*.png");
        return registration;
    }

}