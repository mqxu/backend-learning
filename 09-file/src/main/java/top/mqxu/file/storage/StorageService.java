package top.mqxu.file.storage;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 文件存储能力
 *
 * @author mqxu
 * @date 2026-11-04
 * @description 上层只认这个接口，换成本地磁盘还是 OSS 由配置决定，业务代码不用改
 */
public interface StorageService {

    /**
     * 存储方式标识，local / oss
     */
    String type();

    /**
     * 写入文件，objectKey 由调用方生成并保证唯一
     */
    void write(String objectKey, MultipartFile file);

    /**
     * 读取文件，返回的 StoredObject 必须关闭，否则本地会泄漏文件句柄、OSS 会占着连接
     */
    StoredObject read(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);

    /**
     * 可以直接放进 img 标签或返回给前端的访问地址
     */
    String url(String objectKey);

    /**
     * 带有效期的临时地址。本地存储没有签名机制，直接返回 url
     */
    default String presignedUrl(String objectKey, Duration ttl) {
        return url(objectKey);
    }
}
