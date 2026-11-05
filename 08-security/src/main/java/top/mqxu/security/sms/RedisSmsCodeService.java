package top.mqxu.security.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.config.SecurityProperties;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 验证码的 Redis 实现，多实例部署时也能共享。
 * <p>
 * 三条防线都在这里：发送间隔限制防轰炸、有效期限制防转发、错误次数限制防撞码。
 *
 * @author mqxu
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "mqxu.security.sms.store", havingValue = "redis", matchIfMissing = true)
public class RedisSmsCodeService implements SmsCodeService {

    private static final String CODE_KEY = "sms:code:";
    private static final String LIMIT_KEY = "sms:limit:";
    private static final String FAIL_KEY = "sms:fail:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Duration sendInterval;
    private final int maxFailCount;
    private final SecureRandom random = new SecureRandom();

    public RedisSmsCodeService(StringRedisTemplate redis, SecurityProperties properties) {
        this.redis = redis;
        this.ttl = properties.getSms().getCodeTtl();
        this.sendInterval = properties.getSms().getSendInterval();
        this.maxFailCount = properties.getSms().getMaxFailCount();
    }

    @Override
    public String sendCode(String phone) {
        // setIfAbsent 是原子操作，并发请求只有一个能拿到发送资格
        Boolean first = redis.opsForValue().setIfAbsent(LIMIT_KEY + phone, "1", sendInterval);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }

        // 六位数字，用 %06d 补零：验证码全程当字符串，前导 0 不能被数字类型吃掉
        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(CODE_KEY + phone, code, ttl);

        log.info("[短信模拟] 向 {} 发送验证码：{}", mask(phone), code);
        return code;
    }

    @Override
    public boolean verify(String phone, String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }

        String key = CODE_KEY + phone;
        String saved = redis.opsForValue().get(key);
        if (saved == null) {
            return false;
        }

        if (!saved.equals(code)) {
            countFail(phone, key);
            return false;
        }

        // 一次性：校验通过立刻删除，同一个码不能用第二次
        redis.delete(key);
        redis.delete(FAIL_KEY + phone);
        return true;
    }

    /**
     * 输错计数。到达上限就把验证码一起删掉，逼用户重新发码。
     */
    private void countFail(String phone, String codeKey) {
        String failKey = FAIL_KEY + phone;
        Long fails = redis.opsForValue().increment(failKey);
        if (fails != null && fails == 1L) {
            redis.expire(failKey, ttl);
        }
        if (fails != null && fails >= maxFailCount) {
            redis.delete(codeKey);
            redis.delete(failKey);
            log.warn("手机号 {} 验证码连续输错 {} 次，已作废", mask(phone), fails);
        }
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
