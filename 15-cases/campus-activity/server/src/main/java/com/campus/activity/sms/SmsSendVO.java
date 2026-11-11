package com.campus.activity.sms;

/**
 * 发送验证码接口的响应。
 *
 * @param phone           手机号
 * @param expireSeconds   验证码有效期（秒）
 * @param intervalSeconds 下次可发送的间隔（秒），前端用来做倒计时
 * @param mockCode        Mock 通道且 expose-code=true 时返回验证码，方便本地联调；真实通道为 null
 */
public record SmsSendVO(String phone, long expireSeconds, long intervalSeconds, String mockCode) {
}
