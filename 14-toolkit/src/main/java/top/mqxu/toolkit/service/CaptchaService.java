package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.captcha.AbstractCaptcha;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.core.math.Calculator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.CaptchaResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码服务
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 生成四种样式与算术验证码，答案存在本地缓存里，校验一次即作废
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final int MAX_FAILURES = 3;

    private final ToolkitProperties properties;

    private TimedCache<String, AbstractCaptcha> cache;
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        long ttl = properties.getCaptcha().getExpireSeconds() * 1000;
        cache = CacheUtil.newTimedCache(ttl);
        // 每 30 秒清理一次过期项，避免只增不减
        cache.schedulePrune(30_000);
    }

    @PreDestroy
    void destroy() {
        cache.cancelPruneSchedule();
        cache.clear();
    }

    /**
     * 生成验证码
     *
     * @param type line / circle / shear / gif / math
     */
    public CaptchaResponse create(String type) {
        String style = StrUtil.blankToDefault(type, "line");
        AbstractCaptcha captcha = switch (style) {
            case "circle" -> CaptchaUtil.createCircleCaptcha(120, 40, 4, 8);
            case "shear" -> CaptchaUtil.createShearCaptcha(120, 40, 4, 2);
            case "gif" -> CaptchaUtil.createGifCaptcha(120, 40, 4);
            case "math" -> new LineCaptcha(120, 40, new MathGenerator(1), 2);
            default -> CaptchaUtil.createLineCaptcha(120, 40, 4, 2);
        };

        String captchaId = IdUtil.fastSimpleUUID();
        cache.put(captchaId, captcha);

        // GIF 是多帧图，Base64 前缀是 gif，其余都是 png
        String image = "data:image/" + ("gif".equals(style) ? "gif" : "png")
                + ";base64," + captcha.getImageBase64();

        return new CaptchaResponse(captchaId, image,
                properties.getCaptcha().getExpireSeconds(), style,
                debugAnswer(style, captcha));
    }

    /**
     * 调试开关打开时返回可直接提交的答案。
     * 算术验证码的 code 是算式本身（例如 3*7=），要算出结果才和提交值一致
     */
    private String debugAnswer(String style, AbstractCaptcha captcha) {
        if (!properties.getCaptcha().isDebugCode()) {
            return null;
        }
        String raw = captcha.getCode();
        if (!"math".equals(style)) {
            return raw;
        }
        return String.valueOf((int) Calculator.conversion(StrUtil.removeSuffix(raw, "=")));
    }

    /**
     * 校验验证码，成功后立即删除，防止同一个码重复使用
     */
    public void verify(String captchaId, String code) {
        if (StrUtil.isBlank(captchaId)) {
            throw new BusinessException(ErrorCode.CAPTCHA_NOT_FOUND);
        }
        AbstractCaptcha captcha = cache.get(captchaId, false);
        if (captcha == null) {
            throw new BusinessException(ErrorCode.CAPTCHA_NOT_FOUND);
        }
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.CAPTCHA_WRONG, "请输入验证码");
        }

        if (captcha.verify(code)) {
            cache.remove(captchaId);
            failures.remove(captchaId);
            return;
        }

        int count = failures.merge(captchaId, 1, Integer::sum);
        if (count >= MAX_FAILURES) {
            cache.remove(captchaId);
            failures.remove(captchaId);
            throw new BusinessException(ErrorCode.CAPTCHA_USED, "错误次数过多，验证码已失效");
        }
        throw new BusinessException(ErrorCode.CAPTCHA_WRONG,
                "验证码错误，还可尝试 " + (MAX_FAILURES - count) + " 次");
    }
}
