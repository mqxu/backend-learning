package com.campus.activity.vo;

import lombok.Data;

import java.util.List;

@Data
public class CheckinQrVO {

    private Long activityId;
    private String activityTitle;
    private String token;
    /** 二维码轮换周期，前端按此值定时刷新 */
    private int refreshSeconds;
    private int signedCount;
    private int checkedInCount;
    private List<String> recentCheckins;
}
