package top.mqxu.security.common;

import lombok.Getter;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 错误码枚举：4xx/5xx 与 HTTP 状态码对齐，3xxxx 为业务错误
 **/
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或令牌已失效"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    // 认证与账号相关
    BAD_CREDENTIALS(30001, "用户名或密码错误"),
    ACCOUNT_DISABLED(30002, "账号已被禁用"),
    USER_NOT_FOUND(30003, "用户不存在"),
    USERNAME_EXISTS(30004, "用户名已存在"),
    CANNOT_DELETE_SELF(30005, "不能删除当前登录账号"),

    // 实训任务相关
    TASK_NOT_FOUND(30010, "任务不存在"),
    TASK_DUPLICATED(30011, "同一任务已提交，等待批改"),
    TASK_ALREADY_REVIEWED(30012, "该任务已批改，不能重复批改");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
