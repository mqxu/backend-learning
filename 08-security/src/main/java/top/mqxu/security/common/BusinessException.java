package top.mqxu.security.common;

import lombok.Getter;

/**
 * 业务异常。抛它就会按 errorCode 转成统一的 JSON，不需要在每个接口里写 try-catch。
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
