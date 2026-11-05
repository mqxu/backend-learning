package top.mqxu.doc.common;

import lombok.Getter;

/**
 * 业务异常
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
}
