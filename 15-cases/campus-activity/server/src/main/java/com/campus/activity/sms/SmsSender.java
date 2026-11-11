package com.campus.activity.sms;

/**
 * 短信发送通道抽象。默认 Mock 通道只打日志，生产可切换容联云真实通道。
 *
 * <p>具体实现由 application.yml 中的 campus.sms.provider 决定：
 * mock / cloopen。</p>
 */
public interface SmsSender {

    /** 通道名，仅用于日志与排查 */
    String provider();

    /**
     * 发送验证码短信，失败时抛 {@link com.campus.activity.common.BizException}。
     *
     * @param phone 手机号
     * @param code  6 位数字验证码
     */
    void send(String phone, String code);
}
