package top.mqxu.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description CloopenConfig
 **/
@Data
@Configuration
@ConfigurationProperties(prefix = "mqxu.sms.ccp")
public class CloopenConfig {
    private String serverIp;
    private String port;
    private String accountSID;
    private String accountToken;
    private String appId;
    private String templateId;
}
