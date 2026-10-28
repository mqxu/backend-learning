package top.mqxu.web.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import top.mqxu.web.common.filter.ApiKeyFilter;
import top.mqxu.web.common.filter.ParameterCleanFilter;

/**
 * 过滤器注册：需要在代码里控制路径、顺序、开关时用 FilterRegistrationBean
 *
 * @author mqxu
 */
@Configuration
public class FilterConfig {

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
