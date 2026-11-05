package top.mqxu.zhihu.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtService 的单元测试。不启动 Spring，直接把依赖传进构造器。
 *
 * @author mqxu
 */
class JwtServiceTest {

    private static final String SECRET = "zhihu-case-demo-secret-key-please-change-in-production";

    private final JwtService jwtService = new JwtService(SECRET, 24);

    @Test
    @DisplayName("签发的 token 能解出原来的用户 id")
    void createAndParse() {
        String token = jwtService.createToken(42L, "alice");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtService.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("改掉签名段一个字符就失效")
    void tamperedTokenReturnsNull() {
        String token = jwtService.createToken(42L, "alice");
        // 改签名段的第一个字符，改末尾可能只动到 Base64 的填充位
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart)
                + (original == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);

        assertThat(jwtService.parseUserId(tampered)).isNull();
    }

    @Test
    @DisplayName("换了密钥就验不过")
    void wrongSecretReturnsNull() {
        String token = jwtService.createToken(42L, "alice");
        JwtService other = new JwtService("another-demo-secret-key-with-enough-length-2026", 24);

        assertThat(other.parseUserId(token)).isNull();
    }

    @Test
    @DisplayName("过了有效期就解不出来")
    void expiredTokenReturnsNull() {
        // 有效期设成负数，签发时过期时间就落在过去。
        // 不能用 0：exp 等于当前秒时，jjwt 判定"尚未过期"
        JwtService expired = new JwtService(SECRET, -1);
        String token = expired.createToken(42L, "alice");

        assertThat(expired.parseUserId(token)).isNull();
    }

    @Test
    @DisplayName("乱传字符串不会抛异常，只返回 null")
    void garbageReturnsNull() {
        assertThat(jwtService.parseUserId("not-a-token")).isNull();
        assertThat(jwtService.parseUserId("")).isNull();
    }
}
