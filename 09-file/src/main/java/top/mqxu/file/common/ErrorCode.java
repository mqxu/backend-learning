package top.mqxu.file.common;

import lombok.Getter;

/**
 * 错误码枚举。
 * <p>
 * 三个字段的分工：code 是给前端做判断的业务码，httpStatus 是真正写进响应的 HTTP 状态，
 * message 是给人看的提示。业务码可以超出 HTTP 状态码的取值范围，两者不必相同，
 * 但语义要对应——类型错误映射 400，找不到映射 404，服务端问题映射 500。
 *
 * @author mqxu
 * @date 2026-11-04
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, 200, "success"),
    BAD_REQUEST(400, 400, "参数错误"),
    NOT_FOUND(404, 404, "资源不存在"),
    INTERNAL_ERROR(500, 500, "系统繁忙，请稍后再试"),

    // 文件相关：业务码用 104xx 段，HTTP 状态按语义映射
    FILE_EMPTY(10401, 400, "上传文件为空"),
    FILE_TYPE_NOT_ALLOWED(10402, 400, "文件类型不支持"),
    FILE_TOO_LARGE(10403, 413, "文件超过大小限制"),
    FILE_NOT_FOUND(10404, 404, "文件不存在或已被删除"),
    STORAGE_ERROR(10405, 500, "文件存储失败");

    private final Integer code;
    private final Integer httpStatus;
    private final String message;

    ErrorCode(Integer code, Integer httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
