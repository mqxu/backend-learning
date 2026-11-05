package top.mqxu.toolkit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.CaptchaResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证码服务。
 * <p>
 * 这个类没有依赖外部中间件，缓存是 Hutool 的 TimedCache，直接 new 出来就能测。
 * 重点是四个行为：答案是随机的、答对一次就作废、错满三次也作废、算术题的答案要算出来。
 *
 * @author mqxu
 */
@DisplayName("图形验证码")
class CaptchaServiceTest {

    private ToolkitProperties properties;

    private CaptchaService service;

    @BeforeEach
    void setUp() {
        properties = new ToolkitProperties();
        properties.getCaptcha().setExpireSeconds(60);
        // 打开调试开关，响应里直接带答案，测试才能拿到提交值
        properties.getCaptcha().setDebugCode(true);

        service = new CaptchaService(properties);
        service.init();
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    @DisplayName("生成的是 PNG 的 Base64，带编号与有效期")
    void createReturnsBase64Image() {
        CaptchaResponse response = service.create("line");

        assertThat(response.captchaId()).isNotBlank();
        assertThat(response.image()).startsWith("data:image/png;base64,");
        assertThat(response.expireSeconds()).isEqualTo(60);
        assertThat(response.type()).isEqualTo("line");
        // 调试开关打开时才会带答案
        assertThat(response.code()).hasSize(4);
    }

    @Test
    @DisplayName("GIF 类型的 Base64 前缀不是 png")
    void gifCaptchaUsesGifMimeType() {
        CaptchaResponse response = service.create("gif");

        assertThat(response.image()).startsWith("data:image/gif;base64,");
    }

    @Test
    @DisplayName("答对一次就作废，同一个编号不能再验")
    void correctCodeIsSingleUse() {
        CaptchaResponse response = service.create("line");

        service.verify(response.captchaId(), response.code());

        assertThatThrownBy(() -> service.verify(response.captchaId(), response.code()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码");
    }

    @Test
    @DisplayName("错三次之后编号失效，正确的码也救不回来")
    void threeFailuresInvalidate() {
        CaptchaResponse response = service.create("line");

        // 前两次只是提示，第三次直接作废
        assertThatThrownBy(() -> service.verify(response.captchaId(), "xxxx"))
                .hasMessageContaining("还可尝试 2 次");
        assertThatThrownBy(() -> service.verify(response.captchaId(), "xxxx"))
                .hasMessageContaining("还可尝试 1 次");
        assertThatThrownBy(() -> service.verify(response.captchaId(), "xxxx"))
                .hasMessageContaining("错误次数过多");

        // 作废之后再提交正确答案，也找不到这个编号了
        assertThatThrownBy(() -> service.verify(response.captchaId(), response.code()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("算术验证码返回的是算好的结果，不是算式")
    void mathCaptchaReturnsResult() {
        CaptchaResponse response = service.create("math");

        // 形如 "3*7=" 的算式要算出 21，直接提交算式本身通不过
        assertThat(response.code()).matches("\\d+");
        service.verify(response.captchaId(), response.code());
    }

    @Test
    @DisplayName("不存在的编号报验证码已过期")
    void unknownIdRejected() {
        assertThatThrownBy(() -> service.verify("not-exists", "1234"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码");
    }

    @Test
    @DisplayName("空编号直接拒绝，不会走到缓存查询")
    void blankIdRejected() {
        assertThatThrownBy(() -> service.verify("", "1234"))
                .isInstanceOf(BusinessException.class);
    }
}
