package top.mqxu.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.config.SecurityProperties;
import top.mqxu.security.sms.InMemorySmsCodeService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证码的内存实现。发送间隔与有效期都调到毫秒级，免得测试真的等一分钟。
 *
 * @author mqxu
 */
class InMemorySmsCodeTest {

    private static final String PHONE = "13800000001";

    private InMemorySmsCodeService service;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.getSms().setCodeTtl(Duration.ofSeconds(30));
        properties.getSms().setSendInterval(Duration.ofMillis(50));
        properties.getSms().setMaxFailCount(3);
        service = new InMemorySmsCodeService(properties);
    }

    @Test
    @DisplayName("发出去的验证码能校验通过，且只能用一次")
    void codeWorksOnlyOnce() {
        String code = service.sendCode(PHONE);

        assertThat(service.verify(PHONE, code)).isTrue();
        assertThat(service.verify(PHONE, code)).isFalse();
    }

    @Test
    @DisplayName("输错 3 次后验证码作废，正确码也不再通过")
    void tooManyFailuresInvalidateCode() {
        String code = service.sendCode(PHONE);

        assertThat(service.verify(PHONE, "000000")).isFalse();
        assertThat(service.verify(PHONE, "111111")).isFalse();
        assertThat(service.verify(PHONE, "222222")).isFalse();

        // 达到上限后整条记录被删掉，正确码也救不回来
        assertThat(service.verify(PHONE, code)).isFalse();
    }

    @Test
    @DisplayName("发送间隔内重复发码被拒绝，间隔过去后可以再发")
    void sendIntervalBlocksRepeat() {
        service.sendCode(PHONE);

        assertThatThrownBy(() -> service.sendCode(PHONE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过于频繁");

        // 等过 50 毫秒的冷却期
        sleep(80);
        assertThat(service.sendCode(PHONE)).hasSize(6);
    }

    @Test
    @DisplayName("验证码过期后校验失败")
    void expiredCodeRejected() {
        SecurityProperties properties = new SecurityProperties();
        properties.getSms().setCodeTtl(Duration.ofMillis(30));
        properties.getSms().setSendInterval(Duration.ofMillis(10));
        InMemorySmsCodeService shortLived = new InMemorySmsCodeService(properties);

        String code = shortLived.sendCode(PHONE);
        sleep(60);

        assertThat(shortLived.verify(PHONE, code)).isFalse();
    }

    @Test
    @DisplayName("六位验证码保留前导零")
    void codeKeepsLeadingZero() {
        for (int i = 0; i < 20; i++) {
            sleepQuietly(60);
            String code = service.sendCode("139000000" + (10 + i));
            assertThat(code).hasSize(6).matches("\\d{6}");
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietly(long millis) {
        sleep(millis);
    }
}
