package top.mqxu.toolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.mqxu.toolkit.dto.SensitiveResult;
import top.mqxu.toolkit.dto.TextExtractResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文本清洗。
 * <p>
 * 两条主线：从一段话里把手机号、邮箱、身份证这些捞出来并打码；把敏感词找出来并替换。
 * 提取顺序有讲究，身份证比手机号长，先处理它才不会被误伤。
 *
 * @author mqxu
 */
@DisplayName("文本处理")
class TextServiceTest {

    private TextService service;

    @BeforeEach
    void setUp() {
        service = new TextService();
        service.init();
    }

    @Test
    @DisplayName("手机号被提取出来，原文里换成中间四位打码")
    void extractsAndMasksPhone() {
        TextExtractResult result = service.extract("联系我 13812345678 或 13900001111");

        assertThat(result.phones()).containsExactly("13812345678", "13900001111");
        assertThat(result.maskedText())
                .contains("138****5678")
                .contains("139****1111")
                .doesNotContain("13812345678");
    }

    @Test
    @DisplayName("十八位身份证不会被手机号规则撕成碎片")
    void idCardNotEatenByPhonePattern() {
        String source = "身份证 320102199003071239 请核对";
        TextExtractResult result = service.extract(source);

        assertThat(result.idCards()).containsExactly("320102199003071239");
        // 身份证里的 1990030712 这十位不应当被当成手机号
        assertThat(result.phones()).isEmpty();
        assertThat(result.maskedText()).doesNotContain("320102199003071239");
    }

    @Test
    @DisplayName("邮箱、IP、链接各自打码，保留能辨认的部分")
    void masksEmailIpAndUrl() {
        String source = "邮箱 zhangsan@example.com，IP 192.168.1.100，"
                + "文档 https://example.com/docs/a?id=1";

        TextExtractResult result = service.extract(source);

        assertThat(result.emails()).containsExactly("zhangsan@example.com");
        assertThat(result.ips()).containsExactly("192.168.1.100");
        assertThat(result.urls()).containsExactly("https://example.com/docs/a?id=1");

        // 邮箱保留域名，IP 保留前两段，链接只留域名
        assertThat(result.maskedText()).contains("@example.com");
        // maskIp 保留前两段，后两段各换成一个星号：192.168.*.*
        assertThat(result.maskedText()).contains("192.168.*.*");
        assertThat(result.maskedText()).contains("https://example.com/***");
    }

    @Test
    @DisplayName("一段纯文本没有可提取的东西，原样返回")
    void plainTextUntouched() {
        TextExtractResult result = service.extract("今天天气不错");

        assertThat(result.phones()).isEmpty();
        assertThat(result.emails()).isEmpty();
        assertThat(result.ips()).isEmpty();
        assertThat(result.urls()).isEmpty();
        assertThat(result.idCards()).isEmpty();
        assertThat(result.maskedText()).isEqualTo("今天天气不错");
    }

    @Test
    @DisplayName("空输入不抛异常，返回空结果")
    void nullInputHandled() {
        TextExtractResult result = service.extract(null);

        assertThat(result.maskedText()).isEmpty();
        assertThat(result.phones()).isEmpty();
    }

    @Test
    @DisplayName("敏感词能找出多个，重复的只报一次")
    void findsSensitiveWords() {
        SensitiveResult result = service.filter("这是赌博，也是赌博，还涉及诈骗");

        assertThat(result.hits()).containsExactlyInAnyOrder("赌博", "诈骗");
    }

    @Test
    @DisplayName("替换后的文本用等长星号盖住，长度不变")
    void filteredTextKeepsLength() {
        String source = "严厉打击赌博行为";
        SensitiveResult result = service.filter(source);

        assertThat(result.filtered()).isEqualTo("严厉打击**行为");
        assertThat(result.filtered()).hasSameSizeAs(source);
    }

    @Test
    @DisplayName("高亮版本给原文加 mark，方便前端直接渲染")
    void highlightedWrapsWords() {
        SensitiveResult result = service.filter("发现诈骗线索");

        assertThat(result.highlighted()).isEqualTo("发现<mark>诈骗</mark>线索");
    }

    @Test
    @DisplayName("没有敏感词时，替换结果和原文一致")
    void cleanTextUnchanged() {
        SensitiveResult result = service.filter("这是一段正常的话");

        assertThat(result.hits()).isEmpty();
        assertThat(result.filtered()).isEqualTo("这是一段正常的话");
    }
}
