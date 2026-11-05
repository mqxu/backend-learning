package top.mqxu.security.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Controller 层异常统一转 JSON。
 * <p>
 * 这里只管 Controller 调用栈里抛出的异常。过滤器链里的认证/授权失败走不到这里，
 * 由 JsonAuthenticationEntryPoint 与 JsonAccessDeniedHandler 负责，两边都要写。
 *
 * @author mqxu
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getCode())
                .body(Result.fail(e.getErrorCode().getCode(), e.getMessage()));
    }

    /**
     * 密码错、短信验证码错都走这里。文案用异常自带的消息：
     * 密码登录是“用户名或密码错误”，短信登录是“验证码错误或已过期”。
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<Void>> handleBadCredentials(BadCredentialsException e) {
        log.warn("认证失败：{}", e.getMessage());
        // DaoAuthenticationProvider 抛的是默认英文消息 "Bad credentials"，直接透出去前端没法展示；
        // 短信登录的 Provider 自己写了中文消息，保留它，两种入口各说各的原因
        String message = "Bad credentials".equals(e.getMessage())
                ? ErrorCode.BAD_CREDENTIALS.getMessage()
                : e.getMessage();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ErrorCode.BAD_CREDENTIALS.getCode(), message));
    }

    /**
     * 账号被禁用、锁定或过期。与密码错分开返回，否则用户永远不知道该去找管理员。
     */
    @ExceptionHandler({DisabledException.class, LockedException.class,
            AccountExpiredException.class, CredentialsExpiredException.class})
    public ResponseEntity<Result<Void>> handleAccountUnavailable(Exception e) {
        log.warn("账号不可用：{}", e.getMessage());
        // 这几个异常默认也是英文（User is disabled / User account is locked），逐类换成中文
        String message = e instanceof DisabledException ? "账号已被禁用"
                : e instanceof LockedException ? "账号已被锁定"
                : e instanceof AccountExpiredException ? "账号已过期"
                : "登录凭证已过期";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(ErrorCode.ACCOUNT_UNAVAILABLE.getCode(), message));
    }

    /**
     * 短信登录按手机号查不到账号时由 Provider 直接抛出。对外统一说成凭证错误，避免暴露号码是否注册过。
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Result<Void>> handleUserNotFound(UsernameNotFoundException e) {
        log.warn("用户不存在：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ErrorCode.BAD_CREDENTIALS));
    }

    /**
     * @PreAuthorize 拒绝时抛的是 AuthorizationDeniedException（AccessDeniedException 的子类），
     * 它在 Controller 调用栈里抛出，走不到 ExceptionTranslationFilter，
     * 不在这里接住就会被兜底成 500。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("方法级鉴权拒绝：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg));
    }

    /**
     * @RequestParam 上的 @Pattern 校验失败走这里，需要类上有 @Validated 才生效。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), "缺少必填参数：" + e.getParameterName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(405, "请求方法不支持：" + e.getMethod()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.fail(415, "请求体类型不支持，请使用 application/json"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}
