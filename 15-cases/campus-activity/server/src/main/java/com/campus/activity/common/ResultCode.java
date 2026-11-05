package com.campus.activity.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    OK(0, "ok"),

    // 10xxx 认证与权限
    UNAUTHORIZED(10001, "未登录或登录已过期"),
    FORBIDDEN(10002, "没有操作权限"),
    NEED_BIND(10003, "请先绑定学号信息"),
    STUDENT_NO_NOT_FOUND(10004, "学号不在名单内，请联系管理员"),
    STUDENT_NO_BOUND(10005, "该学号已被其他微信绑定"),
    ACCOUNT_DISABLED(10006, "账号已被禁用"),
    LOGIN_FAILED(10007, "账号或密码错误"),

    // 20xxx 活动
    ACTIVITY_NOT_FOUND(20001, "活动不存在"),
    ACTIVITY_STATUS_INVALID(20002, "活动当前状态不支持该操作"),
    ACTIVITY_NOT_OWNER(20003, "只能操作本组织的活动"),
    ACTIVITY_TIME_INVALID(20004, "时间设置不合理，请检查报名与活动时间"),
    ACTIVITY_NOT_OPEN(20005, "活动暂未开放报名"),

    // 30xxx 报名
    SIGNUP_FULL(30001, "名额已满"),
    SIGNUP_REPEATED(30002, "你已报名该活动"),
    SIGNUP_NOT_FOUND(30003, "报名记录不存在"),
    SIGNUP_CANNOT_CANCEL(30004, "已超过取消报名截止时间"),
    SIGNUP_AUDIT_REJECTED(30005, "报名审核未通过"),
    SIGNUP_STATUS_INVALID(30006, "当前报名状态不支持该操作"),

    // 40xxx 签到
    CHECKIN_TOKEN_INVALID(40001, "签到码无效或已过期，请刷新后重试"),
    CHECKIN_NOT_SIGNED_UP(40002, "你未报名该活动，无法签到"),
    CHECKIN_REPEATED(40003, "你已签到，无需重复操作"),
    CHECKIN_CLOSED(40004, "当前不在签到时间内"),
    CHECKIN_OUT_OF_RANGE(40005, "不在签到范围内，请到活动现场签到"),

    // 50xxx 通用
    PARAM_ERROR(50001, "参数错误"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
