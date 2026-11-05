package top.mqxu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.security.config.SecurityProperties;
import top.mqxu.security.security.JwtService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与校验。不启动 Spring，直接构造 JwtService。
 *
 * @author mqxu
 */
class JwtServiceTest {

    /** HS512 要求密钥不少于 64 字节，这里用 64 个 ASCII 字符 */
    private static final String SECRET =
            "3f2b8d1e7c9a4f6b0d5e8a2c7f1b9d4e6a3c8f2b5d7e9a1c4f6b8d2e5a7c9f1b";

    private JwtService service(int expireMinutes) {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setExpireMinutes(expireMinutes);
        return new JwtService(properties);
    }

    @Test
    @DisplayName("签发后能解析出用户名与权限列表")
    void generateThenParse() {
        JwtService jwt = service(120);

        String token = jwt.generate("alice", List.of("ROLE_USER", "ARTICLE_DELETE"));
        Claims claims = jwt.parse(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get(JwtService.CLAIM_AUTHORITIES, List.class))
                .containsExactly("ROLE_USER", "ARTICLE_DELETE");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("换一个字符的 token 验签就失败")
    void tamperedTokenRejected() {
        JwtService jwt = service(120);
        String token = jwt.generate("alice", List.of("ROLE_USER"));

        // 改签名段的第一个字符。改末尾字符不行：Base64url 末位可能落在无意义位上，
        // 解码后字节没变，验签照样通过
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart)
                + (original == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("用过期的 token 解析抛 ExpiredJwtException")
    void expiredTokenRejected() {
        // 签发时就已经过期
        String token = service(-1).generate("alice", List.of("ROLE_USER"));

        assertThatThrownBy(() -> service(120).parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("别的密钥签的 token 验不过")
    void tokenFromAnotherKeyRejected() {
        SecurityProperties other = new SecurityProperties();
        other.getJwt().setSecret("a".repeat(64));
        String foreign = new JwtService(other).generate("bob", List.of("ROLE_ADMIN"));

        assertThatThrownBy(() -> service(120).parse(foreign)).isInstanceOf(JwtException.class);
    }
}
