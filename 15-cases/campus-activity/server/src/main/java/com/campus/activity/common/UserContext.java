package com.campus.activity.common;

/**
 * 登录用户上下文，由 JwtAuthenticationFilter 写入，Controller / Service 读取。
 * <p>
 * Spring Security 已接管鉴权，但业务代码大量使用 UserContext.requireUserId()，
 * 因此过滤器在写 SecurityContext 的同时也写这里，请求结束再清掉。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.id();
    }

    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user.id();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && "ADMIN".equals(user.role());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
