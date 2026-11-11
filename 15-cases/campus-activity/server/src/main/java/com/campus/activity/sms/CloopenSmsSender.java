package com.campus.activity.sms;

import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import com.campus.activity.config.SmsProperties;
import com.cloopen.rest.sdk.BodyType;
import com.cloopen.rest.sdk.CCPRestSmsSDK;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;

/**
 * 容联云短信通道（campus.sms.provider=cloopen 时生效）。
 *
 * <p>账号密码来自环境变量 CLOOPEN_ACCOUNT_SID / CLOOPEN_ACCOUNT_TOKEN / CLOOPEN_APP_ID，
 * 未配置时应用仍可正常启动（Bean 创建不校验配置），只有真正发短信时才会给出明确异常。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "campus.sms", name = "provider", havingValue = "cloopen")
public class CloopenSmsSender implements SmsSender {

    /** 容联云发送成功的状态码 */
    private static final String STATUS_SUCCESS = "000000";

    private final SmsProperties properties;

    @Override
    public String provider() {
        return "cloopen";
    }

    @Override
    public void send(String phone, String code) {
        SmsProperties.Cloopen config = properties.getCloopen();
        if (!StringUtils.hasText(config.getAccountSid())
                || !StringUtils.hasText(config.getAccountToken())
                || !StringUtils.hasText(config.getAppId())) {
            throw new BizException(ResultCode.SMS_SEND_FAILED,
                    "容联云短信账号未配置，请设置环境变量 CLOOPEN_ACCOUNT_SID / CLOOPEN_ACCOUNT_TOKEN / CLOOPEN_APP_ID");
        }

        CCPRestSmsSDK sdk = new CCPRestSmsSDK();
        sdk.init(config.getServerIp(), String.valueOf(config.getPort()));
        sdk.setAccount(config.getAccountSid(), config.getAccountToken());
        sdk.setAppId(config.getAppId());
        sdk.setBodyType(BodyType.Type_JSON);

        try {
            HashMap<String, Object> result = sdk.sendTemplateSMS(phone, config.getTemplateId(), new String[]{code});
            if (STATUS_SUCCESS.equals(result.get("statusCode"))) {
                log.info("容联云短信发送成功，手机号 {}", mask(phone));
                return;
            }
            log.warn("容联云短信发送失败，手机号 {}，状态码 {}，状态信息 {}",
                    mask(phone), result.get("statusCode"), result.get("statusMsg"));
            throw new BizException(ResultCode.SMS_SEND_FAILED,
                    "短信发送失败，状态码 " + result.get("statusCode"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用容联云短信接口异常", e);
            throw new BizException(ResultCode.SMS_SEND_FAILED, "短信发送失败，请稍后再试");
        }
    }

    private String mask(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
