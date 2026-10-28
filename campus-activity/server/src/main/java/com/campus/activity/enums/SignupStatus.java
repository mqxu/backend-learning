package com.campus.activity.enums;

import lombok.Getter;

@Getter
public enum SignupStatus {

    PENDING(0, "待审核"),
    SUCCESS(1, "报名成功"),
    WAITLIST(2, "候补中"),
    CHECKED_IN(3, "已签到"),
    CANCELLED(4, "已取消"),
    REJECTED(5, "审核未通过"),
    NO_SHOW(6, "未到场");

    private final int code;
    private final String label;

    SignupStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static SignupStatus of(int code) {
        for (SignupStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知报名状态: " + code);
    }

    /** 是否占用活动名额 */
    public boolean occupiesSeat() {
        return this == SUCCESS || this == CHECKED_IN || this == NO_SHOW;
    }

    /** 是否可以取消 */
    public boolean cancellable() {
        return this == PENDING || this == SUCCESS || this == WAITLIST;
    }
}
