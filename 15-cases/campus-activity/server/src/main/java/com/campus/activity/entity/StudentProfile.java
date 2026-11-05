package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_profile")
public class StudentProfile {

    @TableId
    private Long userId;

    private String studentNo;
    private String realName;
    private String college;
    private String grade;
    private String major;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
