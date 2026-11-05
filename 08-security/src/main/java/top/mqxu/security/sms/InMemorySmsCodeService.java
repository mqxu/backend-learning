package top.mqxu.security.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.mqxu.security.common.BusinessException;
import top.mqxu.security.common.ErrorCode;
import top.mqxu.security.config.SecurityProperties;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码的进程内实现。本地没装 Redis 时把 mqxu.security.sms.store 改成 memory 即可。
 * <p>
 * 进程重启就全没了，多实例之间也不共享，只适合跑通链路，不能用于生产。
 *
 * @author mqxu
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "mqxu.security.sms.store", havingValue = "memory")
public class InMemorySmsCodeService implements SmsCodeService {

    private record CodeEntry(String code, int fails, Instant expireAt) {
    }

    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final Map<String, Instant> sendLimits = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private final Duration ttl;
    private final Duration sendInterval;
    private final int maxFailCount;

    public InMemorySmsCodeService(SecurityProperties properties) {
        this.ttl = properties.getSms().getCodeTtl();
        this.sendInterval = properties.getSms().getSendInterval();
        this.maxFailCount = properties.getSms().getMaxFailCount();
    }

    @Override
    public String sendCode(String phone) {
        Instant now = Instant.now();
        Instant limitUntil = sendLimits.get(phone);
        if (limitUntil != null && limitUntil.isAfter(now)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }
        sendLimits.put(phone, now.plus(sendInterval));

        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(phone, new CodeEntry(code, 0, now.plus(ttl)));

        log.info("[短信模拟] 向 {} 发送验证码：{}", mask(phone), code);
        return code;
    }

    @Override
    public boolean verify(String phone, String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }

        CodeEntry entry = codes.get(phone);
        if (entry == null || Instant.now().isAfter(entry.expireAt())) {
            codes.remove(phone);
            return false;
        }

        if (!entry.code().equals(code)) {
            int fails = entry.fails() + 1;
            if (fails >= maxFailCount) {
                codes.remove(phone);
                log.warn("手机号 {} 验证码连续输错 {} 次，已作废", mask(phone), fails);
            } else {
                codes.put(phone, new CodeEntry(entry.code(), fails, entry.expireAt()));
            }
            return false;
        }

        // 一次性：校验通过立刻删除
        codes.remove(phone);
        return true;
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
