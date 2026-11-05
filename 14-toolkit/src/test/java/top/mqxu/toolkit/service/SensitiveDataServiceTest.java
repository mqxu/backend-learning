package top.mqxu.toolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.CreateCustomerRequest;
import top.mqxu.toolkit.dto.CustomerView;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 敏感数据处理。
 * <p>
 * 这个类的重点不是"能不能存"，而是"存进去的是不是密文、不同角色看到的东西是不是不一样"。
 * 两条都要测，只测前者会漏掉脱敏规则写错的情况。
 *
 * @author mqxu
 */
@DisplayName("敏感数据")
class SensitiveDataServiceTest {

    private static final String AES_KEY = "4f8b2d6a1c9e3570";

    private SensitiveDataService service;

    @BeforeEach
    void setUp() {
        ToolkitProperties properties = new ToolkitProperties();
        properties.getCrypto().setAesKey(AES_KEY);

        service = new SensitiveDataService(properties);
        service.init();
    }

    private CreateCustomerRequest sample() {
        return new CreateCustomerRequest("张三", "13812345678", "320102199003071239",
                "secret123", "gold");
    }

    @Test
    @DisplayName("手机号与身份证加密入库，不是明文")
    void sensitiveFieldsEncryptedAtRest() {
        CustomerView created = service.create(sample());
        Map<String, Object> raw = service.raw(created.id());

        assertThat(raw.get("phoneEncrypted")).asString()
                .isNotEqualTo("13812345678")
                .doesNotContain("13812345678");
        assertThat(raw.get("idCardEncrypted")).asString()
                .isNotEqualTo("320102199003071239")
                .doesNotContain("320102199003071239");
    }

    @Test
    @DisplayName("密码存的是 BCrypt 哈希，不是明文也不是可逆加密")
    void passwordStoredAsBcryptHash() {
        CustomerView created = service.create(sample());
        Map<String, Object> raw = service.raw(created.id());

        String hash = String.valueOf(raw.get("passwordHash"));
        assertThat(hash).startsWith("$2");
        assertThat(hash).doesNotContain("secret123");
        // BCrypt 每次加盐不同，同一个密码两次哈希结果不一样
        assertThat(hash).isNotEqualTo(service.raw(service.create(sample()).id()).get("passwordHash"));
    }

    @Test
    @DisplayName("管理员看到完整手机号与身份证")
    void adminSeesFullValues() {
        CustomerView created = service.create(sample());

        CustomerView view = service.get(created.id(), "admin");

        assertThat(view.phone()).isEqualTo("13812345678");
        assertThat(view.idCard()).isEqualTo("320102199003071239");
        // 年龄与性别是从身份证算出来的，不是单独字段
        assertThat(view.age()).isPositive();
        assertThat(view.gender()).isIn("男", "女");
    }

    @Test
    @DisplayName("其他角色看到的是脱敏后的值，但前六后四保留")
    void otherRolesSeeMaskedValues() {
        CustomerView created = service.create(sample());

        CustomerView view = service.get(created.id(), "staff");

        assertThat(view.phone()).isEqualTo("138****5678");
        assertThat(view.idCard()).isEqualTo("320102********1239");
        // 脱敏只影响展示，年龄、性别照样能算出来
        assertThat(view.age()).isPositive();
    }

    @Test
    @DisplayName("密码校验用 BCrypt，明文不会被解密回来")
    void verifyPasswordUsesBcrypt() {
        CustomerView created = service.create(sample());

        assertThat(service.verifyPassword(created.id(), "secret123")).isTrue();
        assertThat(service.verifyPassword(created.id(), "secret124")).isFalse();
        assertThat(service.verifyPassword(created.id(), "")).isFalse();
    }

    @Test
    @DisplayName("手机号、身份证格式不对就拒绝，不落库")
    void invalidInputRejected() {
        CreateCustomerRequest badPhone = new CreateCustomerRequest("李四", "12345",
                "320102199003071239", "secret123", null);
        assertThatThrownBy(() -> service.create(badPhone))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号");

        CreateCustomerRequest badIdCard = new CreateCustomerRequest("李四", "13812345678",
                "32010219900307123X", "secret123", null);
        assertThatThrownBy(() -> service.create(badIdCard))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("身份证");
    }

    @Test
    @DisplayName("密码少于六位就拒绝")
    void shortPasswordRejected() {
        CreateCustomerRequest request = new CreateCustomerRequest("王五", "13812345678",
                "320102199003071239", "12345", null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6 位");
    }

    @Test
    @DisplayName("AES 密钥不是 16 位时启动就失败")
    void wrongKeyLengthFailsFast() {
        ToolkitProperties properties = new ToolkitProperties();
        properties.getCrypto().setAesKey("too-short");

        SensitiveDataService broken = new SensitiveDataService(properties);

        // 不在 init 时拦下来，就会等到第一次加密才崩，而且报的是加密库的内部错误
        assertThatThrownBy(broken::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16 位");
    }
}
