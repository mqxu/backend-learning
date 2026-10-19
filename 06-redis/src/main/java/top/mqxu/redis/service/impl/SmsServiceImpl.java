package top.mqxu.redis.service.impl;

import com.cloopen.rest.sdk.BodyType;
import com.cloopen.rest.sdk.CCPRestSmsSDK;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.mqxu.redis.common.*;
import top.mqxu.redis.config.CloopenConfig;
import top.mqxu.redis.config.SmsProperties;
import top.mqxu.redis.dto.LoginRequest;
import top.mqxu.redis.dto.LoginResponse;
import top.mqxu.redis.service.SmsService;

import java.util.HashMap;
import java.util.Map;

/**
 * @author mqxu
 * @date 2026/10/14
 * @description SmsServiceImpl
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsServiceImpl implements SmsService {

    /**
     * 一天有多少秒，用于“当日发送条数”的计数窗口
     */
    private static final long SECONDS_PER_DAY = 24 * 60 * 60L;
    /**
     * 验证码 Hash 里存验证码的字段
     */
    private static final String FIELD_CODE = "code";
    /**
     * 验证码 Hash 里存状态的字段
     */
    private static final String FIELD_STATUS = "status";
    private static final String STATUS_UNUSED = "0";
    private static final String STATUS_USED = "1";

    private final CloopenConfig cloopenConfig;
    private final SmsProperties smsProperties;
    private final RedisCache redisCache;
    private final JwtUtils jwtUtils;

    @Override
    public void sendSms(String phone) {
        // 1、检查 phone 格式是否正确
        if (!CommonUtils.checkPhone(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ERROR);
        }

        // 2、发送间隔：SET NX EX 只让一次请求占住这个 key，占不到说明刚发过
        String intervalKey = RedisKeys.getSmsSendIntervalKey(phone);
        Boolean acquired = redisCache.setIfAbsent(intervalKey, 1, smsProperties.getSendIntervalSeconds());
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.PHONE_CODE_SEND_TOO_MANY);
        }

        // 3、当日条数：计数器的过期时间在第一次自增时设置
        Long sentToday = redisCache.increment(RedisKeys.getSmsSendCountKey(phone), SECONDS_PER_DAY);
        if (sentToday != null && sentToday > smsProperties.getSendDailyLimit()) {
            throw new BusinessException(ErrorCode.PHONE_CODE_SEND_LIMIT);
        }

        // 4、调用工具方法，生成随机验证码
        int code = CommonUtils.generateCode();

        // 5、先发短信，成功之后再写 Redis：用户没收到短信时不留验证码占位
        if (!send(phone, code)) {
            // 发送失败，把冷却的 key 撤掉，允许立刻重试
            redisCache.delete(intervalKey);
            throw new BusinessException(ErrorCode.PHONE_CODE_SEND_FAILED);
        }

        // 6、验证码与状态一起存成 Hash，方便区分“没用过”和“已经用过了”
        Map<String, Object> value = new HashMap<>();
        value.put(FIELD_CODE, String.valueOf(code));
        value.put(FIELD_STATUS, STATUS_UNUSED);
        redisCache.hMSet(RedisKeys.getSmsKey(phone), value, smsProperties.getCodeExpireSeconds());

        log.info("验证码已下发，手机号 {}", phone);
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String phone = loginRequest.getPhone();
        String inputCode = loginRequest.getCode();

        // 1.校验手机号格式
        if (!CommonUtils.checkPhone(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ERROR);
        }

        // 2.校验验证码是否为空
        if (inputCode == null || inputCode.isBlank()) {
            throw new BusinessException(ErrorCode.PHONE_CODE_EMPTY);
        }

        // 3.从 Redis 中获取验证码，先判空再取值
        String redisKey = RedisKeys.getSmsKey(phone);
        Map<String, Object> cached = redisCache.hGetAll(redisKey);
        if (cached.isEmpty()) {
            throw new BusinessException(ErrorCode.PHONE_CODE_EXPIRED);
        }

        // 4.已经用过的验证码不能再登录一次
        if (STATUS_USED.equals(cached.get(FIELD_STATUS))) {
            throw new BusinessException(ErrorCode.PHONE_CODE_USED);
        }

        // 5.验证码不匹配，连续错到上限就作废，防止四位验证码被逐个试
        if (!inputCode.equals(cached.get(FIELD_CODE))) {
            Long failCount = redisCache.increment(
                    RedisKeys.getSmsFailCountKey(phone), smsProperties.getCodeExpireSeconds());
            if (failCount != null && failCount >= smsProperties.getMaxFailCount()) {
                redisCache.delete(redisKey);
                redisCache.delete(RedisKeys.getSmsFailCountKey(phone));
                log.warn("手机号 {} 验证码连续输错 {} 次，已作废", phone, failCount);
                throw new BusinessException(ErrorCode.PHONE_CODE_ERROR_TOO_MANY);
            }
            throw new BusinessException(ErrorCode.PHONE_CODE_ERROR);
        }

        // 6.验证通过：把状态改成已使用并保留到过期，重复提交能给出明确提示
        redisCache.hSet(redisKey, FIELD_STATUS, STATUS_USED, smsProperties.getCodeExpireSeconds());
        redisCache.delete(RedisKeys.getSmsFailCountKey(phone));

        // 7.签发 token 并返回登录信息
        String token = jwtUtils.create(phone);

        log.info("手机号 {} 登录成功", phone);
        return new LoginResponse(token, phone, jwtUtils.getExpireSeconds());
    }

    /**
     * 调用短信通道。mockEnabled 为 true 时走模拟通道，验证码只写日志，不真正发送
     *
     * @param phone 手机号
     * @param code  验证码
     * @return 是否发送成功
     */
    private boolean send(String phone, int code) {
        if (smsProperties.isMockEnabled()) {
            log.info("短信通道为模拟模式，手机号 {}，验证码 {}", phone, code);
            return true;
        }

        CCPRestSmsSDK sdk = new CCPRestSmsSDK();
        sdk.init(cloopenConfig.getServerIp(), cloopenConfig.getPort());
        sdk.setAccount(cloopenConfig.getAccountSID(), cloopenConfig.getAccountToken());
        sdk.setAppId(cloopenConfig.getAppId());
        sdk.setBodyType(BodyType.Type_JSON);

        String[] dataS = {String.valueOf(code)};
        HashMap<String, Object> result = sdk.sendTemplateSMS(phone, cloopenConfig.getTemplateId(), dataS);

        // 状态码为 000000 表示发送成功，其余情况把错误码与错误信息打出来
        if ("000000".equals(result.get("statusCode"))) {
            log.info("短信发送成功，手机号 {}", phone);
            return true;
        }
        log.warn("短信发送失败，手机号 {}，状态码 {}，状态信息 {}",
                phone, result.get("statusCode"), result.get("statusMsg"));
        return false;
    }
}
