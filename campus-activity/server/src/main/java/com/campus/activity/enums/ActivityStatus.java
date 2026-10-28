package com.campus.activity.enums;

import lombok.Getter;

/**
 * 活动状态。数据库存 code。
 */
@Getter
public enum ActivityStatus {

    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
    REJECTED(2, "已驳回"),
    PUBLISHED(3, "已发布"),
    ONGOING(4, "进行中"),
    FINISHED(5, "已结束"),
    CANCELLED(6, "已取消");

    private final int code;
    private final String label;

    ActivityStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ActivityStatus of(int code) {
        for (ActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知活动状态: " + code);
    }

    /** 学生端可见的状态 */
    public boolean visibleToStudent() {
        return this == PUBLISHED || this == ONGOING || this == FINISHED;
    }
}
