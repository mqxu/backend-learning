package top.mqxu.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 文件模块配置
 *
 * @author mqxu
 * @date 2026-11-04
 * @description 存储位置、大小上限、类型白名单、OSS 密钥全部外置，代码里不写死
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /**
     * 生效的存储方式，local 或 oss
     */
    private StorageType storageType = StorageType.LOCAL;

    /**
     * 允许上传的扩展名（小写，不带点）
     */
    private List<String> allowedExtensions = List.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "txt", "md", "csv",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip");

    /**
     * 单文件大小上限，与 spring.servlet.multipart 的配置保持一致即可
     */
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    /**
     * 对象 key 中日期目录的格式
     */
    private String datePattern = "yyyy/MM/dd";

    private Local local = new Local();
    private Oss oss = new Oss();

    public DateTimeFormatter dateFormatter() {
        return DateTimeFormatter.ofPattern(datePattern);
    }

    @Data
    public static class Local {

        /**
         * 落盘根目录，支持相对路径
         */
        private String baseDir = "./uploads";

        /**
         * 对外访问前缀。用相对路径 /uploads 时由前端或网关补域名；
         * 前面挂了 Nginx 也可以写成 https://static.example.com/uploads
         */
        private String urlPrefix = "/uploads";
    }

    @Data
    public static class Oss {

        /**
         * 地域节点，例如 https://oss-cn-hangzhou.aliyuncs.com
         */
        private String endpoint;

        private String accessKeyId;

        private String accessKeySecret;

        private String bucketName;

        /**
         * 统一放在 bucket 下的哪个目录，留空表示放根目录
         */
        private String dir = "files";

        /**
         * 绑定的自定义域名或 CDN 域名，配了就用它拼访问地址
         */
        private String domain;
    }
}
