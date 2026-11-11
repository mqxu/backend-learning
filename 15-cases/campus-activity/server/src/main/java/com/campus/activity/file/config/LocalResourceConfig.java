package com.campus.activity.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地存储的静态资源映射。
 * 把 campus.file.local.base-dir 挂到 url-prefix/**，上传后的图片可以直接在浏览器里访问。
 * storage-type=oss 时不会装配这个类。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "campus.file", name = "storage-type", havingValue = "local", matchIfMissing = true)
public class LocalResourceConfig implements WebMvcConfigurer {

    private final FileProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = Paths.get(properties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        // 目录可能还不存在，先建出来，否则第一次访问会 404
        base.toFile().mkdirs();

        // 注意：目录 URI 必须以 / 结尾，否则 Spring 会当成单个文件而不是目录
        String location = base.toUri().toString();
        String pattern = trimTrailingSlash(properties.getLocal().getUrlPrefix()) + "/**";
        registry.addResourceHandler(pattern).addResourceLocations(location);
        log.info("本地文件访问映射：{} -> {}", pattern, location);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
