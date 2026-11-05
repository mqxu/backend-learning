package top.mqxu.file.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置
 *
 * @author mqxu
 * @date 2026-11-04
 * @description OSSClient 内部维护连接池，线程安全，整个应用共用一个实例即可，用完要 shutdown
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "file", name = "storage-type", havingValue = "oss")
public class AliyunOssConfig {

    private final FileProperties properties;

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        FileProperties.Oss oss = properties.getOss();
        requireText(oss.getEndpoint(), "file.oss.endpoint");
        requireText(oss.getAccessKeyId(), "file.oss.access-key-id");
        requireText(oss.getAccessKeySecret(), "file.oss.access-key-secret");
        requireText(oss.getBucketName(), "file.oss.bucket-name");

        log.info("启用阿里云 OSS 存储：endpoint={} bucket={} dir={}",
                oss.getEndpoint(), oss.getBucketName(), oss.getDir());
        return new OSSClientBuilder()
                .build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
    }

    private void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "storage-type 设为 oss，但 " + key + " 没有配置；换成本地存储请把 file.storage-type 改成 local");
        }
    }
}
