package com.campus.activity.vo;

import com.campus.activity.entity.StudentProfile;
import com.campus.activity.entity.SysUser;
import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    private String role;
    private Long orgId;
    private String orgName;
    private String phone;

    private String studentNo;
    private String realName;
    private String college;
    private String grade;
    private String major;

    /** 是否已完成学号绑定 */
    private boolean bound;

    public static UserVO of(SysUser user, StudentProfile profile, String orgName) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRole(user.getRole());
        vo.setOrgId(user.getOrgId());
        vo.setOrgName(orgName);
        vo.setPhone(user.getPhone());
        if (profile != null) {
            vo.setStudentNo(profile.getStudentNo());
            vo.setRealName(profile.getRealName());
            vo.setCollege(profile.getCollege());
            vo.setGrade(profile.getGrade());
            vo.setMajor(profile.getMajor());
            vo.setBound(true);
        }
        return vo;
    }
}
