package top.mqxu.toolkit.dto;

/**
 * 验证码响应
 *
 * @param captchaId     验证码标识，校验时回传
 * @param image         data URI，可直接放进 img 的 src
 * @param expireSeconds 有效期（秒）
 * @param type          样式
 * @param code          仅当 toolkit.captcha.debug-code 打开时有值，练习时方便从响应里取答案
 * @author mqxu
 * @date 2026-10-21
 */
public record CaptchaResponse(String captchaId, String image, long expireSeconds, String type, String code) {
}
