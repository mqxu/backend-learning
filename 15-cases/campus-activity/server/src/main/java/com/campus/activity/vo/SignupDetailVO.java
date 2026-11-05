package com.campus.activity.vo;

import com.campus.activity.common.DateTimes;
import com.campus.activity.entity.ActivitySignup;
import com.campus.activity.entity.StudentProfile;
import com.campus.activity.enums.SignupStatus;
import lombok.Data;

@Data
public class SignupDetailVO {

    private Long id;
    private Long userId;
    private String studentNo;
    private String realName;
    private String college;
    private String grade;

    private Integer status;
    private String statusLabel;
    private String signupNo;
    private String formData;
    private Integer waitlistSeq;

    private String createTime;
    private String checkinTime;

    public static SignupDetailVO of(ActivitySignup signup, StudentProfile profile) {
        SignupDetailVO vo = new SignupDetailVO();
        vo.setId(signup.getId());
        vo.setUserId(signup.getUserId());
        vo.setStatus(signup.getStatus());
        vo.setStatusLabel(SignupStatus.of(signup.getStatus()).getLabel());
        vo.setSignupNo(signup.getSignupNo());
        vo.setFormData(signup.getFormData());
        vo.setWaitlistSeq(signup.getWaitlistSeq());
        vo.setCreateTime(DateTimes.format(signup.getCreateTime()));
        vo.setCheckinTime(DateTimes.format(signup.getCheckinTime()));
        if (profile != null) {
            vo.setStudentNo(profile.getStudentNo());
            vo.setRealName(profile.getRealName());
            vo.setCollege(profile.getCollege());
            vo.setGrade(profile.getGrade());
        }
        return vo;
    }
}
