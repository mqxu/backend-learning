package com.campus.activity.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 文件模块配置，对应 application.yml 的 campus.file.*。
 * 存储方式、大小上限、类型白名单、OSS 密钥全部外置，代码里不写死。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.file")
public class FileProperties {

    /** 生效的存储方式：local 落本地磁盘，oss 传阿里云对象存储 */
    private StorageType storageType = StorageType.LOCAL;

    /** 允许上传的扩展名（小写，不带点） */
    private List<String> allowedExtensions = List.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "txt", "md", "csv",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip");

    /** 单文件大小上限，与 spring.servlet.multipart 的配置保持一致 */
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    /** objectKey 中日期目录的格式，例如 yyyy/MM/dd */
    private String datePattern = "yyyy/MM/dd";

    private Local local = new Local();
    private Oss oss = new Oss();

    /** 日期目录格式化器，按配置的 date-pattern 生成 */
    public DateTimeFormatter dateFormatter() {
        return DateTimeFormatter.ofPattern(datePattern);
    }

    /** 本地磁盘存储配置 */
    @Data
    public static class Local {

        /** 落盘根目录，支持相对路径，默认 ./uploads */
        private String baseDir = "./uploads";

        /** 对外访问前缀，默认 /uploads；挂了 Nginx 可换成完整域名 */
        private String urlPrefix = "/uploads";
    }

    /** 阿里云 OSS 存储配置 */
    @Data
    public static class Oss {

        /** 地域节点，例如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;

        private String accessKeyId;

        private String accessKeySecret;

        private String bucketName;

        /** 统一放在 bucket 下的哪个目录，留空表示放根目录 */
        private String dir = "files";

        /** 绑定的自定义域名或 CDN 域名，配了就用它拼访问地址 */
        private String domain;
    }
}
