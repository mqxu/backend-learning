package com.campus.activity.sms;

import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import com.campus.activity.config.SmsProperties;
import com.campus.activity.support.RedisCache;
import com.campus.activity.support.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 短信验证码服务：发送、校验与三条防线。
 *
 * <p>验证码与状态存 Redis Hash：field=code 是验证码，field=status 是使用状态，
 * 过期时间由 campus.sms.code-expire-seconds 控制（默认 300 秒）。</p>
 *
 * <p>防刷与防撞码：</p>
 * <ul>
 *   <li>发送间隔：SET NX EX 占位，冷却期内重复发送直接拒绝</li>
 *   <li>当日上限：按手机号计数，超过 send-daily-limit 拒绝</li>
 *   <li>错误次数：连续输错达到 max-fail-count 作废验证码</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    /** 验证码 Hash 中存验证码的字段 */
    public static final String FIELD_CODE = "code";
    /** 验证码 Hash 中存使用状态的字段 */
    public static final String FIELD_STATUS = "status";
    /** 未使用 */
    public static final String STATUS_UNUSED = "0";
    /** 已使用 */
    public static final String STATUS_USED = "1";

    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /** 一天的秒数，用于“当日发送条数”的计数窗口 */
    private static final long SECONDS_PER_DAY = 24L * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisCache redisCache;
    private final SmsProperties properties;
    private final SmsSender smsSender;

    /**
     * 生成并发送验证码。
     *
     * @return 生成的验证码；是否回传给前端由上层根据 mock + exposeCode 决定
     */
    public String sendCode(String phone) {
        checkPhone(phone);

        // 1. 发送间隔：SET NX EX 只让一次请求占住这个 key，占不到说明刚发过
        String intervalKey = RedisKeys.smsInterval(phone);
        if (!redisCache.setIfAbsent(intervalKey, "1", properties.getSendIntervalSeconds())) {
            throw new BizException(ResultCode.SMS_CODE_SEND_TOO_MANY);
        }

        // 2. 当日条数：计数器的过期时间在第一次自增时设置
        Long sentToday = redisCache.increment(RedisKeys.smsDaily(phone), SECONDS_PER_DAY);
        if (sentToday != null && sentToday > properties.getSendDailyLimit()) {
            redisCache.delete(intervalKey);
            throw new BizException(ResultCode.SMS_CODE_DAILY_LIMIT);
        }

        // 3. 六位数字验证码，%06d 补零：验证码全程当字符串，前导 0 不能被数字类型吃掉
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        // 4. 先发短信，成功之后再写 Redis：用户没收到短信时不留验证码占位
        try {
            smsSender.send(phone, code);
        } catch (RuntimeException e) {
            // 发送失败，把冷却的 key 撤掉，允许立刻重试
            redisCache.delete(intervalKey);
            throw e;
        }

        // 5. 验证码与状态一起存成 Hash，方便区分“没用过”和“已经用过了”
        Map<String, String> value = new HashMap<>();
        value.put(FIELD_CODE, code);
        value.put(FIELD_STATUS, STATUS_UNUSED);
        redisCache.hMSet(RedisKeys.smsCode(phone), value, properties.getCodeExpireSeconds());

        log.info("验证码已下发，手机号 {}，通道 {}", mask(phone), smsSender.provider());
        return code;
    }

    /**
     * 校验验证码，通过后立即标记为已使用。失败按原因抛不同业务码。
     */
    public void verify(String phone, String inputCode) {
        checkPhone(phone);
        if (!StringUtils.hasText(inputCode)) {
            throw new BizException(ResultCode.SMS_CODE_ERROR);
        }

        String codeKey = RedisKeys.smsCode(phone);
        Map<String, String> cached = redisCache.hGetAll(codeKey);
        if (cached.isEmpty()) {
            throw new BizException(ResultCode.SMS_CODE_EXPIRED);
        }

        // 已经用过的验证码不能再登录一次
        if (STATUS_USED.equals(cached.get(FIELD_STATUS))) {
            throw new BizException(ResultCode.SMS_CODE_USED);
        }

        if (!inputCode.equals(cached.get(FIELD_CODE))) {
            countFail(phone, codeKey);
            throw new BizException(ResultCode.SMS_CODE_ERROR);
        }

        // 验证通过：状态改成已使用并保留到过期，重复提交能给出明确提示
        redisCache.hSet(codeKey, FIELD_STATUS, STATUS_USED, properties.getCodeExpireSeconds());
        redisCache.delete(RedisKeys.smsFail(phone));
        log.info("手机号 {} 验证码校验通过", mask(phone));
    }

    /** 是否为 Mock 通道（Mock 且 exposeCode 打开时才会把验证码回传前端） */
    public boolean isMock() {
        return properties.isMock();
    }

    private void countFail(String phone, String codeKey) {
        Long failCount = redisCache.increment(RedisKeys.smsFail(phone), properties.getCodeExpireSeconds());
        if (failCount != null && failCount >= properties.getMaxFailCount()) {
            redisCache.delete(codeKey);
            redisCache.delete(RedisKeys.smsFail(phone));
            log.warn("手机号 {} 验证码连续输错 {} 次，已作废", mask(phone), failCount);
            throw new BizException(ResultCode.SMS_CODE_ERROR_TOO_MANY);
        }
    }

    private void checkPhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException(ResultCode.PHONE_FORMAT_INVALID);
        }
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
