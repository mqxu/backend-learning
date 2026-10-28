package com.campus.activity.vo;

import lombok.Data;

@Data
public class ActivityStatVO {

    private Long activityId;
    private String activityTitle;
    private Integer capacity;
    private Integer signedCount;

    private int totalSignup;
    private int successCount;
    private int pendingCount;
    private int waitlistCount;
    private int checkedInCount;
    private int cancelledCount;
    private int noShowCount;

    /** 到场率 = 已签到 / 报名成功（含已签到） */
    private double attendanceRate;
}
