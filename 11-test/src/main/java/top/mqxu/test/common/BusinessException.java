package top.mqxu.test.common;

import lombok.Getter;

/**
 * 业务异常。订单场景只需要两个业务错误，够测试用了。
 *
 * @author mqxu
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException stockNotEnough(int required) {
        return new BusinessException(30409, "库存不足，需要 " + required + " 件");
    }

    public static BusinessException amountInvalid() {
        return new BusinessException(30400, "金额必须大于 0");
    }
}
