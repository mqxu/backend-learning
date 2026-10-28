package com.campus.activity.vo;

import com.campus.activity.common.DateTimes;
import com.campus.activity.entity.Activity;
import com.campus.activity.enums.ActivityStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityItemVO {

    private Long id;
    private String title;
    private String coverUrl;
    private String summary;

    private Long categoryId;
    private String categoryName;

    private Long orgId;
    private String orgName;

    private String startTime;
    private String endTime;
    private String signupStartTime;
    private String signupEndTime;
    private String locationName;

    private Integer capacity;
    private Integer signedCount;

    private Integer status;
    private String statusLabel;

    /** 当前是否处于可报名状态（时间窗内、名额、状态） */
    private boolean signupOpen;

    /** 当前用户对该活动的报名状态，未登录或未报名为 null */
    private Integer mySignupStatus;

    public static ActivityItemVO of(Activity activity, String categoryName, String orgName) {
        ActivityItemVO vo = new ActivityItemVO();
        vo.setId(activity.getId());
        vo.setTitle(activity.getTitle());
        vo.setCoverUrl(activity.getCoverUrl());
        vo.setSummary(activity.getSummary());
        vo.setCategoryId(activity.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setOrgId(activity.getOrgId());
        vo.setOrgName(orgName);
        vo.setStartTime(DateTimes.formatMinute(activity.getStartTime()));
        vo.setEndTime(DateTimes.formatMinute(activity.getEndTime()));
        vo.setSignupStartTime(DateTimes.formatMinute(activity.getSignupStartTime()));
        vo.setSignupEndTime(DateTimes.formatMinute(activity.getSignupEndTime()));
        vo.setLocationName(activity.getLocationName());
        vo.setCapacity(activity.getCapacity());
        vo.setSignedCount(activity.getSignedCount());
        vo.setStatus(activity.getStatus());
        ActivityStatus status = ActivityStatus.of(activity.getStatus());
        vo.setStatusLabel(status.getLabel());
        vo.setSignupOpen(isSignupOpen(activity));
        return vo;
    }

    public static boolean isSignupOpen(Activity activity) {
        if (activity.getStatus() == null || activity.getStatus() != ActivityStatus.PUBLISHED.getCode()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean inWindow = activity.getSignupStartTime() != null
                && activity.getSignupEndTime() != null
                && !now.isBefore(activity.getSignupStartTime())
                && !now.isAfter(activity.getSignupEndTime());
        return inWindow && activity.hasSeat();
    }
}
