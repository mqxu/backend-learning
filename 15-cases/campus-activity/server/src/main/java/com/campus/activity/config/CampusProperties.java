package com.campus.activity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campus")
public class CampusProperties {

    /** 开发模式：允许 mock openid 登录，不请求微信服务器 */
    private boolean devMode = false;

    private Jwt jwt = new Jwt();
    private Wechat wechat = new Wechat();
    private Checkin checkin = new Checkin();

    @Data
    public static class Jwt {
        private String secret;
        private int accessTokenHours = 2;
    }

    @Data
    public static class Wechat {
        private String appId;
        private String appSecret;
    }

    @Data
    public static class Checkin {
        private String secret;
        private int sliceSeconds = 30;
        private int toleranceSlices = 2;
        private int openBeforeMinutes = 30;
        private int closeAfterMinutes = 30;
    }
}
