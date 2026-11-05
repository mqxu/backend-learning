package com.campus.activity.vo;

import com.campus.activity.common.DateTimes;
import com.campus.activity.entity.Activity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActivityDetailVO extends ActivityItemVO {

    private String content;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String cancelDeadline;
    private Integer waitlistEnabled;
    private Integer auditRequired;
    private Integer checkinType;
    private String formFields;
    private Integer viewCount;
    private Integer favoriteCount;
    private boolean favorited;

    /** 底部按钮文案：立即报名 / 已报名 / 名额已满 / 报名未开始 / 活动已结束 */
    private String actionText;

    /** 当前用户在该活动下的报名记录 ID，未报名为 null */
    private Long mySignupId;

    public static ActivityDetailVO of(Activity activity, String categoryName, String orgName) {
        ActivityDetailVO vo = new ActivityDetailVO();
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
        vo.setStatusLabel(com.campus.activity.enums.ActivityStatus.of(activity.getStatus()).getLabel());
        vo.setSignupOpen(isSignupOpen(activity));

        vo.setContent(activity.getContent());
        vo.setAddress(activity.getAddress());
        vo.setLatitude(activity.getLatitude());
        vo.setLongitude(activity.getLongitude());
        vo.setCancelDeadline(DateTimes.format(activity.getCancelDeadline()));
        vo.setWaitlistEnabled(activity.getWaitlistEnabled());
        vo.setAuditRequired(activity.getAuditRequired());
        vo.setCheckinType(activity.getCheckinType());
        vo.setFormFields(activity.getFormFields());
        vo.setViewCount(activity.getViewCount());
        vo.setFavoriteCount(activity.getFavoriteCount());
        return vo;
    }
}
