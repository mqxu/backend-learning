package com.campus.activity.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 短信通道：不调用任何第三方接口，只把验证码打到日志里。
 *
 * <p>本地开发和课堂演示默认走这个通道；配合 campus.sms.expose-code=true，
 * 验证码还会通过接口响应返回，方便 curl / Swagger 直接登录。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "campus.sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public void send(String phone, String code) {
        log.info("[短信模拟] 向 {} 发送验证码：{}", mask(phone), code);
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
