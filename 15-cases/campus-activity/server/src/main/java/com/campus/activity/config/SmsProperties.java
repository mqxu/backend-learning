package com.campus.activity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信验证码策略参数，对应 application.yml 中的 campus.sms 前缀。
 *
 * <p>这些数字前端也要用（倒计时、有效期提示），改动时两边一起改。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.sms")
public class SmsProperties {

    /** 短信通道：mock（默认，验证码只打日志）/ cloopen（容联云真实通道） */
    private String provider = "mock";

    /** 演示开关：true 时把验证码回传给前端，方便本地 curl / Swagger 联调，生产必须 false */
    private boolean exposeCode = false;

    /** 验证码有效期，单位秒 */
    private long codeExpireSeconds = 300;

    /** 同一个手机号两次发送之间的最小间隔，单位秒 */
    private long sendIntervalSeconds = 60;

    /** 同一个手机号一天最多发送的条数 */
    private long sendDailyLimit = 10;

    /** 同一个验证码最多允许输错的次数，超过就作废 */
    private int maxFailCount = 5;

    /** 容联云短信通道配置，provider=cloopen 时才真正使用 */
    private Cloopen cloopen = new Cloopen();

    /** 是否使用 Mock 通道（不真正发短信） */
    public boolean isMock() {
        return provider == null || "mock".equalsIgnoreCase(provider.trim());
    }

    @Data
    public static class Cloopen {
        private String serverIp = "app.cloopen.com";
        private int port = 8883;
        private String accountSid;
        private String accountToken;
        private String appId;
        private String templateId;
    }
}
