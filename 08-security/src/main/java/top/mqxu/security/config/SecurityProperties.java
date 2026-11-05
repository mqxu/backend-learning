package top.mqxu.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * mqxu.security.* 的配置绑定。
 *
 * @author mqxu
 */
@Data
@ConfigurationProperties(prefix = "mqxu.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private Sms sms = new Sms();
    private Cors cors = new Cors();

    @Data
    public static class Jwt {
        /** HS512 要求密钥不少于 64 字节 */
        private String secret;
        private long expireMinutes = 120;
    }

    @Data
    public static class Sms {
        /** redis 或 memory */
        private String store = "memory";
        /** 把验证码写回响应，仅本地调试用 */
        private boolean exposeCode = false;
        private Duration codeTtl = Duration.ofMinutes(5);
        private Duration sendInterval = Duration.ofSeconds(60);
        private int maxFailCount = 5;
        private boolean autoRegister = true;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
