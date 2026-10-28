package com.campus.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_whitelist")
public class StudentWhitelist {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String studentNo;
    private String realName;
    private String college;
    private String grade;
    private String major;
    private Integer usedFlag;
    private LocalDateTime createTime;
}
