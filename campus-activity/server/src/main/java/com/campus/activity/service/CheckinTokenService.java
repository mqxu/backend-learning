package com.campus.activity.service;

import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import com.campus.activity.config.CampusProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 动态签到码：HMAC(activityId + 时间片)，服务端只保存密钥，不落库。
 * 二维码每 sliceSeconds 秒轮换一次，截图转发最多在容忍窗口内有效。
 */
@Slf4j
@Service
public class CheckinTokenService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final int sliceSeconds;
    private final int toleranceSlices;

    public CheckinTokenService(CampusProperties properties) {
        this.secret = properties.getCheckin().getSecret().getBytes(StandardCharsets.UTF_8);
        this.sliceSeconds = Math.max(5, properties.getCheckin().getSliceSeconds());
        this.toleranceSlices = Math.max(0, properties.getCheckin().getToleranceSlices());
    }

    public int refreshSeconds() {
        return sliceSeconds;
    }

    public String generate(Long activityId) {
        long slice = currentSlice();
        return activityId + "." + slice + "." + sign(activityId, slice);
    }

    /** 校验通过返回活动 ID，失败抛业务异常 */
    public Long verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ResultCode.CHECKIN_TOKEN_INVALID);
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new BizException(ResultCode.CHECKIN_TOKEN_INVALID);
        }
        Long activityId;
        long slice;
        try {
            activityId = Long.valueOf(parts[0]);
            slice = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.CHECKIN_TOKEN_INVALID);
        }

        long now = currentSlice();
        for (int i = 0; i <= toleranceSlices; i++) {
            long candidate = now - i;
            if (candidate == slice && sign(activityId, candidate).equals(parts[2])) {
                return activityId;
            }
        }
        throw new BizException(ResultCode.CHECKIN_TOKEN_INVALID);
    }

    private long currentSlice() {
        return System.currentTimeMillis() / 1000 / sliceSeconds;
    }

    private String sign(Long activityId, long slice) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] raw = mac.doFinal((activityId + ":" + slice).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("签到码签名失败", e);
        }
    }
}
