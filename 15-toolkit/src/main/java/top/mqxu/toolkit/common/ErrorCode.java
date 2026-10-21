package top.mqxu.toolkit.common;

import lombok.Getter;

/**
 * 错误码
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 每个案例的错误码独立编号，41001 起按案例分段
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(40000, "请求参数不合法"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(41500, "不支持的内容类型"),
    PAYLOAD_TOO_LARGE(41300, "上传文件过大"),
    INTERNAL_ERROR(50000, "服务器内部错误"),

    // 案例 1：验证码
    CAPTCHA_NOT_FOUND(41001, "验证码不存在或已过期"),
    CAPTCHA_WRONG(41002, "验证码错误"),
    CAPTCHA_USED(41003, "验证码已使用"),

    // 案例 2：扫码登录
    QR_NOT_FOUND(41011, "二维码不存在或已过期"),
    QR_STATE_CONFLICT(41012, "二维码状态不允许该操作"),

    // 案例 4：短链
    SHORT_LINK_NOT_FOUND(41021, "短链不存在或已过期"),

    // 案例 5：Excel
    EXCEL_READ_FAILED(41031, "Excel 解析失败"),
    EXCEL_EMPTY(41032, "Excel 没有数据行"),

    // 案例 6/7：文件
    CHUNK_SESSION_NOT_FOUND(41041, "分片会话不存在或已过期"),
    CHUNK_NOT_COMPLETE(41042, "分片不完整"),
    CHUNK_MERGE_FAILED(41043, "分片合并失败"),
    FILE_TYPE_BLOCKED(41044, "文件类型不允许"),
    FILE_EMPTY(41045, "文件为空"),

    // 案例 8：开放接口签名
    SIGN_MISSING(41051, "缺少签名参数"),
    SIGN_INVALID(41052, "签名不正确"),
    SIGN_EXPIRED(41053, "请求已过期"),
    SIGN_REPLAY(41054, "重复请求"),

    // 案例 9：敏感数据
    CUSTOMER_NOT_FOUND(41061, "客户不存在");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
