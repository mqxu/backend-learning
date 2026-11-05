package top.mqxu.zhihu.common;

import lombok.Getter;

/**
 * 业务异常。业务层只管抛，HTTP 状态由 GlobalExceptionHandler 统一翻译。
 *
 * @author mqxu
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
