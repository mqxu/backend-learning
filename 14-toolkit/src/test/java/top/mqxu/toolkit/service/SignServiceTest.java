package top.mqxu.toolkit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.SignResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 开放接口签名。
 * <p>
 * 这里守的是四道闸：appId 对不对、时间戳在不在窗口内、签名对不对、nonce 用没用过。
 * 前三道常见，第四道是防重放的关键。
 *
 * @author mqxu
 */
@DisplayName("接口签名")
class SignServiceTest {

    private static final String SECRET = "3f2a9c1e5b7d4086a1c3e5f7092b4d68";

    private SignService service;

    @BeforeEach
    void setUp() {
        ToolkitProperties properties = new ToolkitProperties();
        properties.getSign().setAppId("toolkit-demo");
        properties.getSign().setAppSecret(SECRET);
        properties.getSign().setToleranceSeconds(300);

        service = new SignService(properties);
        service.init();
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    @DisplayName("规范串按 appId、时间戳、nonce、body 四行拼接")
    void canonicalIsFourLines() {
        SignResponse response = service.demo("toolkit-demo", 1700000000L, "abc123",
                "{\"id\":1}");

        assertThat(response.canonical())
                .isEqualTo("toolkit-demo\n1700000000\nabc123\n{\"id\":1}");
        assertThat(response.sign()).hasSize(64);
    }

    @Test
    @DisplayName("自己算的签名能通过校验")
    void validSignaturePasses() {
        SignResponse response = service.demo(null, null, null, "{\"id\":1}");

        service.verify(response.appId(), response.timestamp(), response.nonce(),
                response.sign(), response.body());
    }

    @Test
    @DisplayName("同一个 nonce 第二次提交算重放")
    void sameNonceIsReplay() {
        SignResponse response = service.demo(null, null, "nonce-1", "{}");

        service.verify(response.appId(), response.timestamp(), response.nonce(),
                response.sign(), response.body());

        assertThatThrownBy(() -> service.verify(response.appId(), response.timestamp(),
                response.nonce(), response.sign(), response.body()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("改过 body 之后签名对不上")
    void tamperedBodyRejected() {
        SignResponse response = service.demo(null, null, "nonce-2", "{\"amount\":1}");

        assertThatThrownBy(() -> service.verify(response.appId(), response.timestamp(),
                response.nonce(), response.sign(), "{\"amount\":999}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("时间戳超出容差窗口被拒")
    void expiredTimestampRejected() {
        long tooOld = System.currentTimeMillis() / 1000 - 301;
        SignResponse response = service.demo(null, tooOld, "nonce-3", "{}");

        assertThatThrownBy(() -> service.verify(response.appId(), response.timestamp(),
                response.nonce(), response.sign(), response.body()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("300 秒");
    }

    @Test
    @DisplayName("appId 不在白名单里直接拒，不算签名")
    void unknownAppIdRejected() {
        SignResponse response = service.demo("other-app", null, "nonce-4", "{}");

        assertThatThrownBy(() -> service.verify(response.appId(), response.timestamp(),
                response.nonce(), response.sign(), response.body()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appId");
    }

    @Test
    @DisplayName("四个参数缺任何一个都报缺参数")
    void missingParameterRejected() {
        assertThatThrownBy(() -> service.verify("toolkit-demo", null, "n", "s", "{}"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.verify(null, "1700000000", "n", "s", "{}"))
                .isInstanceOf(BusinessException.class);
    }
}
