package top.mqxu.redis.common;

import lombok.Getter;

/**
 * 错误码枚举
 *
 * @author mqxu
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    //业务异常
    PHONE_ERROR(40001, "手机号格式错误"),
    PHONE_NOT_EXIST(40002, "手机号不存在"),
    PHONE_CODE_EMPTY(40003, "验证码不能为空"),
    PHONE_CODE_ERROR(40004, "验证码错误"),
    PHONE_CODE_EXPIRED(40005, "验证码不存在或已过期"),
    PHONE_CODE_USED(40006, "验证码已使用"),
    PHONE_CODE_SEND_TOO_MANY(40007, "验证码发送过于频繁"),
    PHONE_CODE_SEND_FAILED(40008, "验证码发送失败"),
    PHONE_CODE_SEND_LIMIT(40009, "验证码发送次数已达上限"),
    PHONE_CODE_ERROR_TOO_MANY(40010, "验证码错误次数过多，请重新获取"),

    //登录态
    TOKEN_INVALID(40100, "登录状态已失效，请重新登录");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
