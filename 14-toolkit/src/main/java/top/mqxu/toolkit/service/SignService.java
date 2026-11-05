package top.mqxu.toolkit.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HmacAlgorithm;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.SignResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 开放接口签名
 *
 * @author mqxu
 * @date 2026-10-21
 * @description appId + 时间戳 + nonce + body 拼成规范串，用 HMAC-SHA256 签名，
 * 服务端校验时间窗、签名、nonce 是否用过
 */
@Service
@RequiredArgsConstructor
public class SignService {

    private final ToolkitProperties properties;

    /**
     * 已用过的 nonce，有效期取时间窗的两倍
     */
    private TimedCache<String, Boolean> nonceCache;

    @PostConstruct
    void init() {
        nonceCache = CacheUtil.newTimedCache(
                properties.getSign().getToleranceSeconds() * 2000);
        nonceCache.schedulePrune(60_000);
    }

    @PreDestroy
    void destroy() {
        nonceCache.cancelPruneSchedule();
        nonceCache.clear();
    }

    /**
     * 生成签名，供调用方参考，也方便 curl 直接测
     */
    public SignResponse demo(String appId, Long timestamp, String nonce, String body) {
        String realAppId = StrUtil.blankToDefault(appId, properties.getSign().getAppId());
        long ts = timestamp == null ? System.currentTimeMillis() / 1000 : timestamp;
        String realNonce = StrUtil.blankToDefault(nonce, RandomUtil.randomString(12));
        String realBody = StrUtil.nullToEmpty(body);

        String canonical = canonical(realAppId, String.valueOf(ts), realNonce, realBody);
        String sign = SecureUtil.hmac(HmacAlgorithm.HmacSHA256,
                        properties.getSign().getAppSecret())
                .digestHex(canonical);

        return new SignResponse(realAppId, String.valueOf(ts), realNonce,
                realBody, canonical, sign);
    }

    /**
     * 校验签名，通过后记录 nonce，重复提交会被拒绝
     */
    public void verify(String appId, String timestamp, String nonce, String sign, String body) {
        if (StrUtil.hasBlank(appId, timestamp, nonce, sign)) {
            throw new BusinessException(ErrorCode.SIGN_MISSING);
        }
        if (!properties.getSign().getAppId().equals(appId)) {
            throw new BusinessException(ErrorCode.SIGN_INVALID, "appId 不存在");
        }

        long ts = Convert.toLong(timestamp, 0L);
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > properties.getSign().getToleranceSeconds()) {
            throw new BusinessException(ErrorCode.SIGN_EXPIRED,
                    "请求时间与服务器相差超过 " + properties.getSign().getToleranceSeconds() + " 秒");
        }

        String expected = SecureUtil.hmac(HmacAlgorithm.HmacSHA256,
                        properties.getSign().getAppSecret())
                .digestHex(canonical(appId, timestamp, nonce, StrUtil.nullToEmpty(body)));

        // 常量时间比较，避免通过响应时间猜签名
        boolean matched = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            throw new BusinessException(ErrorCode.SIGN_INVALID);
        }

        String nonceKey = appId + ":" + nonce;
        if (nonceCache.containsKey(nonceKey)) {
            throw new BusinessException(ErrorCode.SIGN_REPLAY);
        }
        nonceCache.put(nonceKey, Boolean.TRUE);
    }

    private String canonical(String appId, String timestamp, String nonce, String body) {
        return appId + "\n" + timestamp + "\n" + nonce + "\n" + body;
    }
}
