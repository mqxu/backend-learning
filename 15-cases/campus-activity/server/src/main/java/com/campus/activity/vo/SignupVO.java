package com.campus.activity.vo;

import com.campus.activity.common.DateTimes;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivitySignup;
import com.campus.activity.enums.SignupStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SignupVO {

    private Long id;
    private Long activityId;
    private String activityTitle;
    private String coverUrl;
    private String startTime;
    private String endTime;
    private String locationName;

    private Integer status;
    private String statusLabel;
    private String signupNo;
    private Integer waitlistSeq;

    private String createTime;
    private String checkinTime;
    private String cancelDeadline;

    /** 活动开始前可取消的截止时间已过则为 false */
    private boolean canCancel;
    /** 是否处于可签到时间窗 */
    private boolean canCheckin;

    public static SignupVO of(ActivitySignup signup, Activity activity) {
        SignupVO vo = new SignupVO();
        vo.setId(signup.getId());
        vo.setActivityId(signup.getActivityId());
        vo.setStatus(signup.getStatus());
        vo.setStatusLabel(SignupStatus.of(signup.getStatus()).getLabel());
        vo.setSignupNo(signup.getSignupNo());
        vo.setWaitlistSeq(signup.getWaitlistSeq());
        vo.setCreateTime(DateTimes.format(signup.getCreateTime()));
        vo.setCheckinTime(DateTimes.format(signup.getCheckinTime()));

        if (activity != null) {
            vo.setActivityTitle(activity.getTitle());
            vo.setCoverUrl(activity.getCoverUrl());
            vo.setStartTime(DateTimes.format(activity.getStartTime()));
            vo.setEndTime(DateTimes.format(activity.getEndTime()));
            vo.setLocationName(activity.getLocationName());
            vo.setCancelDeadline(DateTimes.format(activity.getCancelDeadline()));
        }

        SignupStatus status = SignupStatus.of(signup.getStatus());
        LocalDateTime now = LocalDateTime.now();
        boolean withinCancel = activity == null || activity.getCancelDeadline() == null
                || now.isBefore(activity.getCancelDeadline());
        vo.setCanCancel(status.cancellable() && withinCancel);
        vo.setCanCheckin(status == SignupStatus.SUCCESS);
        return vo;
    }
}
