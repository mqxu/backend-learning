package top.mqxu.toolkit.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 携带错误码，由全局异常处理器转成 Result
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
