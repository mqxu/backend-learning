package top.mqxu.toolkit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 案例配置
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 密钥、过期时间、目录等全部外置，代码里不写死
 */
@Data
@Component
@ConfigurationProperties(prefix = "toolkit")
public class ToolkitProperties {

    /**
     * 临时文件根目录
     */
    private String workDir;

    private Captcha captcha = new Captcha();
    private QrLogin qrLogin = new QrLogin();
    private ShortLink shortLink = new ShortLink();
    private Sign sign = new Sign();
    private Crypto crypto = new Crypto();
    private Chunk chunk = new Chunk();
    private Inspect inspect = new Inspect();

    @Data
    public static class Captcha {
        /**
         * 验证码有效期（秒）
         */
        private long expireSeconds = 60;

        /**
         * 调试开关，打开后响应里带上答案，方便 curl 验证。
         * 只用于本地练习，线上必须关掉
         */
        private boolean debugCode = false;
    }

    @Data
    public static class QrLogin {
        /**
         * 二维码有效期（秒）
         */
        private long expireSeconds = 120;
    }

    @Data
    public static class ShortLink {
        /**
         * 短链有效期（秒），默认 7 天
         */
        private long expireSeconds = 604800;

        /**
         * 拼短链时用的域名前缀
         */
        private String baseUrl = "http://127.0.0.1:8015";
    }

    @Data
    public static class Sign {
        private String appId = "toolkit-demo";
        private String appSecret;
        /**
         * 允许的时间偏差（秒）
         */
        private long toleranceSeconds = 300;
    }

    @Data
    public static class Crypto {
        /**
         * AES 密钥，16 位对应 AES-128
         */
        private String aesKey;
    }

    @Data
    public static class Chunk {
        /**
         * 分片大小（字节），默认 1MB
         */
        private long chunkSize = 1048576;
        /**
         * 会话保留时间（秒）
         */
        private long expireSeconds = 3600;
    }

    @Data
    public static class Inspect {
        private int timeoutMillis = 3000;
        private int maxConcurrency = 8;
    }
}
