package top.mqxu.file.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 * <p>
 * 文件模块最容易踩的两个坑单独兜住：超出大小限制、请求不是 multipart。
 * 响应同时给出业务码与对应的 HTTP 状态，前端按 code 判断，监控按状态码统计。
 *
 * @author mqxu
 * @date 2026-11-04
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        // 用异常自己的消息：业务层常会补一句更具体的提示（"不支持的扩展名：sh"），
        // 这里若只传 errorCode，那句话就被吞掉，前端只剩枚举里的通用文案
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode.getCode(), e.getMessage()));
    }

    /**
     * 容器在解析请求体阶段就把请求拦掉了，进不了 Controller，只能在这里兜
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过限制：{}", e.getMessage());
        return build(ErrorCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException e) {
        log.warn("multipart 请求解析失败：{}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请用 multipart/form-data 提交文件"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        log.warn("参数校验失败：{}", msg);
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), "参数类型错误"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("系统异常", e);
        return build(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(Result.fail(errorCode));
    }
}
