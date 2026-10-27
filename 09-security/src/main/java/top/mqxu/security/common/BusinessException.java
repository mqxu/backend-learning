package top.mqxu.security.common;

import lombok.Getter;

/**
 * @author mqxu
 * @date 2026/10/28
 * @description 业务异常，由全局异常处理器统一转成 Result
 **/
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
