package top.mqxu.zhihu.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码。code 给前端判断，httpStatus 给网关和监控看。
 *
 * <p>约定：业务码的后三位就是 HTTP 状态，40000 段的 400xx 对应 400，
 * 401xx 对应 401，以此类推。这样日志里看到一个码就知道该返回什么状态。</p>
 *
 * @author mqxu
 */
@Getter
public enum ErrorCode {

    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数有误"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "没有权限执行该操作"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "内容不存在"),
    CONFLICT(40900, HttpStatus.CONFLICT, "数据冲突"),
    INTERNAL(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器开小差了");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
