package top.mqxu.web.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import top.mqxu.web.filter.ApiKeyFilter;
import top.mqxu.web.filter.LogFilter;
import top.mqxu.web.filter.MyFilter;
import top.mqxu.web.filter.ParameterCleanFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description FilterConfig
 **/
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<LogFilter> logFilterFilterRegistrationBean() {
        FilterRegistrationBean<LogFilter> registration = new FilterRegistrationBean<>(new LogFilter());
        // 设置拦截路径（/* 为匹配所有请求）
        List<String> urlPatterns = new ArrayList<>();
        urlPatterns.add("/*");
        registration.setUrlPatterns(urlPatterns);
        // 执行顺序（值越小越先执行）
        registration.setOrder(1);
        // 排除静态资源（可选）
        registration.addInitParameter("exclusions", "*.js,*.css,*.png");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<MyFilter> myFilterFilterRegistrationBean() {
        FilterRegistrationBean<MyFilter> registration = new FilterRegistrationBean<>(new MyFilter());
        List<String> urlPatterns = new ArrayList<>();
        urlPatterns.add("/*");
        registration.setUrlPatterns(urlPatterns);
        registration.setOrder(2);
        registration.addInitParameter("exclusions", "*.js,*.css,*.png");
        return registration;
    }


    /**
     * 参数清洗：所有请求都过一遍
     */
    @Bean
    public FilterRegistrationBean<ParameterCleanFilter> parameterCleanFilter() {
        FilterRegistrationBean<ParameterCleanFilter> bean = new FilterRegistrationBean<>(new ParameterCleanFilter());
        bean.setName("parameterCleanFilter");
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }

    /**
     * 密钥校验：只拦 /demo/secure，顺序放在清洗之后
     */
    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter() {
        FilterRegistrationBean<ApiKeyFilter> bean = new FilterRegistrationBean<>(new ApiKeyFilter());
        bean.setName("apiKeyFilter");
        bean.addUrlPatterns("/demo/secure");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return bean;
    }
}