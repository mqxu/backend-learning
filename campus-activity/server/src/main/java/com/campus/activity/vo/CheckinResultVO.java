package com.campus.activity.vo;

import lombok.Data;

@Data
public class CheckinResultVO {

    private boolean success;
    private String message;
    private Long activityId;
    private String activityTitle;
    private String checkinTime;
    private String studentName;

    public static CheckinResultVO ok(Long activityId, String title, String checkinTime, String studentName) {
        CheckinResultVO vo = new CheckinResultVO();
        vo.setSuccess(true);
        vo.setMessage("签到成功");
        vo.setActivityId(activityId);
        vo.setActivityTitle(title);
        vo.setCheckinTime(checkinTime);
        vo.setStudentName(studentName);
        return vo;
    }
}
