package top.mqxu.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地存储的静态资源映射
 *
 * @author mqxu
 * @date 2026-11-04
 * @description 把磁盘目录挂到 /uploads/**，上传完的图片可以直接在浏览器里看
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "file", name = "storage-type", havingValue = "local", matchIfMissing = true)
public class LocalResourceConfig implements WebMvcConfigurer {

    private final FileProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = Paths.get(properties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        // 目录可能还不存在，先建出来，否则第一次访问会 404
        base.toFile().mkdirs();

        String location = base.toUri().toString();
        String pattern = properties.getLocal().getUrlPrefix() + "/**";
        registry.addResourceHandler(pattern).addResourceLocations(location);
        log.info("本地文件访问映射：{} -> {}", pattern, location);
    }
}
