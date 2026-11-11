package com.campus.activity.file.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import com.campus.activity.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Date;

/**
 * 阿里云 OSS 存储。
 * bucket 建议设为私有读，对外一律给预签名地址，链接过期自动失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "campus.file", name = "storage-type", havingValue = "oss")
public class AliyunOssStorageService implements StorageService {

    private static final String NO_SUCH_KEY = "NoSuchKey";

    private final OSS ossClient;
    private final FileProperties properties;

    @Override
    public String type() {
        return "oss";
    }

    @Override
    public void write(String objectKey, MultipartFile file) {
        String key = fullKey(objectKey);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (file.getContentType() != null) {
            metadata.setContentType(file.getContentType());
        }
        // 显式告诉浏览器按下载处理，避免上传的 html 被 OSS 域名直接渲染成页面
        metadata.setContentDisposition("attachment");
        try (InputStream in = file.getInputStream()) {
            ossClient.putObject(bucket(), key, in, metadata);
            log.info("文件写入 OSS：{}（{} 字节）", key, file.getSize());
        } catch (IOException | OSSException e) {
            log.error("写入 OSS 失败：{}", key, e);
            throw new BizException(ResultCode.FILE_STORAGE_ERROR, "上传到对象存储失败");
        }
    }

    @Override
    public StoredObject read(String objectKey) {
        String key = fullKey(objectKey);
        try {
            OSSObject object = ossClient.getObject(bucket(), key);
            ObjectMetadata metadata = object.getObjectMetadata();
            String contentType = metadata.getContentType();
            return new StoredObject(objectKey, metadata.getContentLength(),
                    contentType == null ? StoredObject.DEFAULT_CONTENT_TYPE : contentType,
                    object.getObjectContent());
        } catch (OSSException e) {
            if (NO_SUCH_KEY.equals(e.getErrorCode())) {
                throw new BizException(ResultCode.FILE_NOT_FOUND);
            }
            log.error("读取 OSS 对象失败：{}", key, e);
            throw new BizException(ResultCode.FILE_STORAGE_ERROR, "读取对象存储失败");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return ossClient.doesObjectExist(bucket(), fullKey(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        String key = fullKey(objectKey);
        if (!exists(objectKey)) {
            throw new BizException(ResultCode.FILE_NOT_FOUND);
        }
        ossClient.deleteObject(bucket(), key);
        log.info("删除 OSS 对象：{}", key);
    }

    @Override
    public String url(String objectKey) {
        return baseUrl() + "/" + fullKey(objectKey);
    }

    @Override
    public String presignedUrl(String objectKey, Duration ttl) {
        Date expiration = new Date(System.currentTimeMillis() + ttl.toMillis());
        return ossClient.generatePresignedUrl(bucket(), fullKey(objectKey), expiration).toString();
    }

    private String bucket() {
        return properties.getOss().getBucketName();
    }

    /** 统一前缀，保证所有对象都落在配置的目录下 */
    private String fullKey(String objectKey) {
        String dir = properties.getOss().getDir();
        if (dir == null || dir.isBlank()) {
            return objectKey;
        }
        String prefix = dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
        return prefix + "/" + objectKey;
    }

    /** 配了自定义域名就用它，否则用 bucket 默认域名拼 */
    private String baseUrl() {
        String domain = properties.getOss().getDomain();
        if (domain != null && !domain.isBlank()) {
            return trimSlash(domain);
        }
        String endpoint = properties.getOss().getEndpoint();
        String scheme = "https://";
        if (endpoint.startsWith("http://")) {
            scheme = "http://";
            endpoint = endpoint.substring("http://".length());
        } else if (endpoint.startsWith("https://")) {
            endpoint = endpoint.substring("https://".length());
        }
        return scheme + bucket() + "." + trimSlash(endpoint);
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
