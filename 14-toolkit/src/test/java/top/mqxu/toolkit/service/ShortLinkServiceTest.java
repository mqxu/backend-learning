package top.mqxu.toolkit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.ShortLinkResponse;
import top.mqxu.toolkit.dto.ShortLinkStats;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 短链服务。
 * <p>
 * 值得测的三件事：短码里没有容易看混的字符、点一次计一次、非法地址与不存在的码都要拒绝。
 *
 * @author mqxu
 */
@DisplayName("短链")
class ShortLinkServiceTest {

    /** 生成短码用的字母表，去掉了 0O1lI */
    private static final String ALPHABET =
            "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";

    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        ToolkitProperties properties = new ToolkitProperties();
        properties.getShortLink().setExpireSeconds(3600);
        properties.getShortLink().setBaseUrl("http://127.0.0.1:8014/");

        service = new ShortLinkService(properties);
        service.init();
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    @DisplayName("短码六位，且不含 0 O 1 l I")
    void codeAvoidsAmbiguousCharacters() {
        Set<Character> forbidden = new HashSet<>(Set.of('0', 'O', '1', 'l', 'I'));

        for (int i = 0; i < 20; i++) {
            ShortLinkResponse response = service.create("https://example.com/a", null);

            assertThat(response.code()).hasSize(6);
            assertThat(response.code().chars().mapToObj(c -> (char) c))
                    .allMatch(c -> ALPHABET.indexOf(c) >= 0)
                    .noneMatch(forbidden::contains);
        }
    }

    @Test
    @DisplayName("baseUrl 末尾的斜杠不会拼出双斜杠")
    void shortUrlHasNoDoubleSlash() {
        ShortLinkResponse response = service.create("https://example.com/path?q=1", null);

        assertThat(response.shortUrl())
                .isEqualTo("http://127.0.0.1:8014/toolkit/s/" + response.code());
        assertThat(response.url()).isEqualTo("https://example.com/path?q=1");
    }

    @Test
    @DisplayName("解析返回原地址，点一次计一次")
    void resolveCountsClicks() {
        ShortLinkResponse response = service.create("https://example.com", null);

        assertThat(service.resolve(response.code())).isEqualTo("https://example.com");
        assertThat(service.resolve(response.code())).isEqualTo("https://example.com");
        service.resolve(response.code());

        ShortLinkStats stats = service.stats(response.code());
        assertThat(stats.clicks()).isEqualTo(3);
        assertThat(stats.code()).isEqualTo(response.code());
    }

    @Test
    @DisplayName("没点过的短链点击数从 0 开始")
    void statsStartFromZero() {
        ShortLinkResponse response = service.create("https://example.com", null);

        ShortLinkStats stats = service.stats(response.code());
        assertThat(stats.clicks()).isZero();
        assertThat(stats.expireAt()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    @DisplayName("不是合法地址就拒绝，不生成短码")
    void invalidUrlRejected() {
        assertThatThrownBy(() -> service.create("不是地址", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("url");
    }

    @Test
    @DisplayName("短码不存在时报错，不会返回空字符串")
    void unknownCodeRejected() {
        assertThatThrownBy(() -> service.resolve("zzzzzz"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("短链");
    }

    @Test
    @DisplayName("自定义有效期会覆盖默认值")
    void customTtlOverridesDefault() {
        ShortLinkResponse response = service.create("https://example.com", 60L);
        long expected = System.currentTimeMillis() + 60_000;

        // 允许几百毫秒的执行误差
        assertThat(response.expireAt()).isBetween(expected - 1000, expected + 1000);
    }
}
