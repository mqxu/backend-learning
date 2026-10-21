package top.mqxu.toolkit.service;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.SensitiveProcessor;
import cn.hutool.dfa.SensitiveUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.dto.SensitiveResult;
import top.mqxu.toolkit.dto.TextExtractResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本清洗
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 正则提取手机号、邮箱、IP、链接、身份证并脱敏，敏感词用 DFA 树匹配
 */
@Service
public class TextService {

    private static final List<String> SENSITIVE_WORDS =
            List.of("暴力", "赌博", "诈骗", "违禁品", "代开发票");

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern IP = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern URL =
            Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[\\dXx]\\b");

    @PostConstruct
    void init() {
        SensitiveUtil.init(SENSITIVE_WORDS);
    }

    public TextExtractResult extract(String text) {
        String source = StrUtil.nullToEmpty(text);

        List<String> idCards = ReUtil.findAllGroup0(ID_CARD, source);
        List<String> phones = ReUtil.findAllGroup0(PHONE, source);
        List<String> emails = ReUtil.findAllGroup0(EMAIL, source);
        List<String> urls = ReUtil.findAllGroup0(URL, source);
        List<String> ips = ReUtil.findAllGroup0(IP, source);

        // 先处理长度更长的身份证，避免 18 位里的数字被手机号规则误伤
        String masked = source;
        for (String value : idCards) {
            masked = masked.replace(value, DesensitizedUtil.idCardNum(value, 6, 4));
        }
        for (String value : phones) {
            masked = masked.replace(value, DesensitizedUtil.mobilePhone(value));
        }
        for (String value : emails) {
            masked = masked.replace(value, DesensitizedUtil.email(value));
        }
        for (String value : urls) {
            masked = masked.replace(value, maskUrl(value));
        }
        for (String value : ips) {
            masked = masked.replace(value, maskIp(value));
        }

        return new TextExtractResult(phones, emails, ips, urls, idCards, masked);
    }

    public SensitiveResult filter(String text) {
        String source = StrUtil.nullToEmpty(text);

        List<String> hits = SensitiveUtil.getFoundAllSensitive(source).stream()
                .map(FoundWord::getWord)
                .distinct()
                .toList();

        String filtered = SensitiveUtil.sensitiveFilter(source, true, new SensitiveProcessor() {
            @Override
            public String process(FoundWord word) {
                return "*".repeat(word.getWord().length());
            }
        });
        String highlighted = SensitiveUtil.sensitiveFilter(source, true, new SensitiveProcessor() {
            @Override
            public String process(FoundWord word) {
                return "<mark>" + word.getWord() + "</mark>";
            }
        });

        return new SensitiveResult(hits, filtered, highlighted);
    }

    /**
     * 保留协议和域名，路径统一打码
     */
    private String maskUrl(String url) {
        String host = ReUtil.getGroup0("^https?://[^/]+", url);
        return StrUtil.isBlank(host) ? "/***" : host + "/***";
    }

    /**
     * 保留前两段，后两段打码
     */
    private String maskIp(String ip) {
        return ip.replaceAll("^(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}$", "$1.*.*");
    }
}
