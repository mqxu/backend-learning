package top.mqxu.zhihu.common;

/**
 * 当前登录用户。拦截器解析 token 后写进来，业务层直接读，不用一层层传参数。
 *
 * <p>用 ThreadLocal 的前提是：请求结束后必须清理。
 * 容器的线程是复用的，不清理会把上一个请求的用户带给下一个请求。</p>
 *
 * @author mqxu
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        CURRENT_USER.set(userId);
    }

    /**
     * 可能为 null，用于"登录了就走个性化逻辑，没登录也能看"的接口。
     */
    public static Long get() {
        return CURRENT_USER.get();
    }

    /**
     * 必须登录的接口用这个，没登录直接抛 401。
     */
    public static Long requireUserId() {
        Long userId = CURRENT_USER.get();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
