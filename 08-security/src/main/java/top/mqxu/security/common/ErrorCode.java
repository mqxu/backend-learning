package top.mqxu.security.common;

import lombok.Getter;

/**
 * 业务错误码。数字与 HTTP 状态码对齐，前端拿 code 就能决定是跳登录还是弹提示。
 *
 * @author mqxu
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "ok"),

    BAD_REQUEST(400, "参数不合法"),

    /** 匿名访问受保护资源，或 token 无效 */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /** 登录时账号密码不匹配 */
    BAD_CREDENTIALS(401, "用户名或密码错误"),

    /** 已登录但权限不够 */
    FORBIDDEN(403, "没有访问权限"),

    /** 账号被禁用或锁定 */
    ACCOUNT_UNAVAILABLE(403, "账号已被禁用或锁定"),

    NOT_FOUND(404, "资源不存在"),

    /** 验证码发得太勤 */
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    INTERNAL_ERROR(500, "服务器内部错误");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
