package top.mqxu.doc.common;

import lombok.Getter;

/**
 * 错误码
 *
 * @author mqxu
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    BOOK_NOT_FOUND(20404, "图书不存在"),
    BOOK_ISBN_DUPLICATED(20409, "ISBN 已存在");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
