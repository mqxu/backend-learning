package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_signup")
public class ActivitySignup {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;
    private Long userId;
    private String signupNo;

    /** 见 SignupStatus */
    private Integer status;
    private Integer waitlistSeq;

    /** 自定义字段答案，JSON 字符串 */
    private String formData;

    private Long auditUserId;
    private LocalDateTime auditTime;
    private LocalDateTime cancelTime;
    private LocalDateTime checkinTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
