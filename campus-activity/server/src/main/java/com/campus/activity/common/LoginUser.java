package com.campus.activity.common;

/**
 * 从 JWT 解析出的登录身份。
 */
public record LoginUser(Long id, String role, Long orgId, String nickname) {

    public boolean isStudent() {
        return "STUDENT".equals(role);
    }

    public boolean isOrganizer() {
        return "ORGANIZER".equals(role);
    }

    public boolean isReviewer() {
        return "REVIEWER".equals(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /** 组织者与管理员都可以管理活动 */
    public boolean canManageActivity() {
        return isOrganizer() || isReviewer() || isAdmin();
    }
}
